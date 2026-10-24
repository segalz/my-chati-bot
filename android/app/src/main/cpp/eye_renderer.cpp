#include "eye_renderer.hpp"

#include <android/choreographer.h>
#include <android/log.h>
#include <android/looper.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <memory>

// Original espp/expressive_eyes v1.3.9 engine and its Monochrome Blue example drawer, unmodified.
#include "expressive_eyes.hpp"
#include "extra_drawers.hpp"
#include "monochrome_blue_drawer.hpp"

namespace chati {
namespace {

constexpr const char *kTag = "ChatiEyes";
constexpr float kMaxFrameDt = 0.1f;  // after a stall, do not jump the animation forward

using Clock = std::chrono::steady_clock;
const Clock::time_point kProcessStart = Clock::now();

uint32_t lvgl_tick_ms() {
    return static_cast<uint32_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - kProcessStart).count());
}

constexpr uint32_t kDefaultColor = 0x00BFFF;  // the original drawer's electric blue

void lvgl_log(lv_log_level_t level, const char *message) {
    __android_log_print(level >= LV_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, "LVGL",
                        "%s", message);
}

/// Everything that only the render thread may touch.
struct RenderState {
    std::recursive_mutex lvgl_mutex;  // required by the upstream drawer's Config
    lv_color_t *canvas_buffer = nullptr;
    lv_obj_t *canvas = nullptr;
    std::unique_ptr<eye_drawer::EyeDrawer> drawer;  // the original drawer unless a shape is chosen
    int shape = static_cast<int>(Shape::ORIGINAL);
    eye_drawer::EyeDrawer::DrawCallback draw;  // the current drawer's callback
    std::unique_ptr<espp::ExpressiveEyes> eyes;
    // Eye color (0xRRGGBB): the drawer's default electric blue, then fades set by SetColor.
    uint32_t color = kDefaultColor;
    uint32_t fade_from = kDefaultColor;
    uint32_t fade_to = kDefaultColor;
    float fade_s = 0.0f;
    float fade_t = 1.0f;  // 1 = no fade running
    // The latest eye states, so a color change is drawn at once even when the engine has nothing
    // new to draw.
    espp::ExpressiveEyes::EyeState last_left{};
    espp::ExpressiveEyes::EyeState last_right{};
    bool have_last = false;
    Clock::time_point last_draw{};
};

uint32_t lerp_rgb(uint32_t a, uint32_t b, float t) {
    auto ch = [&](int shift) {
        const float x = static_cast<float>((a >> shift) & 0xff);
        const float y = static_cast<float>((b >> shift) & 0xff);
        return static_cast<uint32_t>(std::lround(x + (y - x) * t)) & 0xff;
    };
    return (ch(16) << 16) | (ch(8) << 8) | ch(0);
}

RenderState *g_state = nullptr;  // created and used on the render thread only

}  // namespace

EyeRenderer &EyeRenderer::instance() {
    static EyeRenderer renderer;
    return renderer;
}

void EyeRenderer::start() {
    if (started_.exchange(true)) return;
    thread_ = std::thread([this] { thread_main(); });
    thread_.detach();  // lives for the whole process
}

void EyeRenderer::attach_window(ANativeWindow *window, int32_t owner) {
    if (window == nullptr) return;
    ANativeWindow_acquire(window);
    ANativeWindow *previous;
    {
        std::lock_guard<std::mutex> lock(window_mutex_);
        previous = window_;
        window_ = window;
        window_owner_ = owner;
        window_needs_geometry_ = true;
    }
    if (previous != nullptr) ANativeWindow_release(previous);
    // Resume frame scheduling if the render thread is sleeping without a window.
    if (ALooper *looper = looper_.load(); looper != nullptr) ALooper_wake(looper);
}

void EyeRenderer::detach_window(int32_t owner) {
    ANativeWindow *previous = nullptr;
    {
        std::lock_guard<std::mutex> lock(window_mutex_);
        if (window_ == nullptr || window_owner_ != owner) {
            __android_log_print(ANDROID_LOG_INFO, kTag, "detach from %d ignored (owner %d)", owner, window_owner_);
            return;
        }
        previous = window_;
        window_ = nullptr;
        window_owner_ = 0;
        window_needs_geometry_ = false;
    }
    ANativeWindow_release(previous);
}

bool EyeRenderer::has_window() {
    std::lock_guard<std::mutex> lock(window_mutex_);
    return window_ != nullptr;
}

void EyeRenderer::post(EyeCommand command) {
    std::lock_guard<std::mutex> lock(command_mutex_);
    commands_.push_back(command);
}

void EyeRenderer::record_interval(int64_t interval_us) {
    if (interval_us < 0) return;
    const int bucket = static_cast<int>(std::min<int64_t>(interval_us / 1000, kIntervalBuckets - 1));
    interval_hist_[bucket].fetch_add(1, std::memory_order_relaxed);
    if (interval_us > 50000) interval_over50_.fetch_add(1, std::memory_order_relaxed);
    const auto us = static_cast<uint32_t>(std::min<int64_t>(interval_us, UINT32_MAX));
    uint32_t seen = interval_max_us_.load(std::memory_order_relaxed);
    while (us > seen && !interval_max_us_.compare_exchange_weak(seen, us, std::memory_order_relaxed)) {}
}

/// Totals since start, plus the frame-interval tail since the previous call (int_*; reset here):
/// int_frames, int_p99_us (upper edge of the 1 ms bucket holding the 99th percentile; the last,
/// open bucket reports at least 200 ms), int_max_us, int_over50 (intervals longer than 50 ms).
/// The counters are reset one by one, so a frame landing during a call may be counted in a
/// neighbouring window — fine for a sampling metric.
std::string EyeRenderer::stats() {
    const uint64_t frames = frames_.load();
    const uint64_t avg_us = frames ? work_us_total_.load() / frames : 0;
    bool has_window;
    {
        std::lock_guard<std::mutex> lock(window_mutex_);
        has_window = window_ != nullptr;
    }
    uint32_t hist[kIntervalBuckets];
    uint64_t count = 0;
    for (int i = 0; i < kIntervalBuckets; ++i) {
        hist[i] = interval_hist_[i].exchange(0, std::memory_order_relaxed);
        count += hist[i];
    }
    const uint32_t max_us = interval_max_us_.exchange(0, std::memory_order_relaxed);
    const uint64_t over50 = interval_over50_.exchange(0, std::memory_order_relaxed);
    uint64_t p99_us = 0;
    if (count > 0) {
        const uint64_t rank = (count * 99 + 99) / 100;  // nearest rank
        uint64_t seen = 0;
        for (int i = 0; i < kIntervalBuckets; ++i) {
            seen += hist[i];
            if (seen >= rank) {
                p99_us = i == kIntervalBuckets - 1
                             ? std::max<uint64_t>(static_cast<uint64_t>(kIntervalBuckets - 1) * 1000, max_us)
                             : static_cast<uint64_t>(i + 1) * 1000;
                break;
            }
        }
    }
    return "frames=" + std::to_string(frames) + " redraws=" + std::to_string(redraws_.load()) +
           " presents=" + std::to_string(presents_.load()) + " work_avg_us=" + std::to_string(avg_us) +
           " work_max_us=" + std::to_string(work_us_max_.load()) +
           " window=" + (has_window ? "yes" : "no") + " int_frames=" + std::to_string(count) +
           " int_p99_us=" + std::to_string(p99_us) + " int_max_us=" + std::to_string(max_us) +
           " int_over50=" + std::to_string(over50);
}

void EyeRenderer::thread_main() {
    looper_.store(ALooper_prepare(0));

    lv_init();
    lv_tick_set_cb(lvgl_tick_ms);
    lv_log_register_print_cb(lvgl_log);
    if (lv_display_create(kCanvasWidth, kCanvasHeight) == nullptr) {
        __android_log_print(ANDROID_LOG_FATAL, kTag, "lv_display_create failed");
        std::abort();
    }
    // The display is never refreshed: lv_timer_handler() is not called. With LV_OS_NONE the
    // drawer's lv_canvas_finish_layer() renders synchronously into the canvas buffer, and the
    // host copies that buffer to the Android surface itself.

    g_state = new RenderState();
    RenderState &s = *g_state;

    // Same canvas setup as the upstream example: W*H*sizeof(lv_color_t) bytes, RGB565.
    const size_t bytes = static_cast<size_t>(kCanvasWidth) * kCanvasHeight * sizeof(lv_color_t);
    s.canvas_buffer = static_cast<lv_color_t *>(std::aligned_alloc(64, (bytes + 63) / 64 * 64));
    if (s.canvas_buffer == nullptr) {
        __android_log_print(ANDROID_LOG_FATAL, kTag, "canvas buffer allocation failed");
        std::abort();
    }
    lv_obj_remove_flag(lv_screen_active(), LV_OBJ_FLAG_SCROLLABLE);
    s.canvas = lv_canvas_create(lv_screen_active());
    lv_canvas_set_buffer(s.canvas, s.canvas_buffer, kCanvasWidth, kCanvasHeight, LV_COLOR_FORMAT_RGB565);
    lv_obj_center(s.canvas);
    lv_canvas_fill_bg(s.canvas, lv_color_black(), LV_OPA_COVER);
    canvas_dirty_ = true;

    set_drawer_color(kDefaultColor);

    // Eye geometry exactly as in the upstream example.
    espp::ExpressiveEyes::Config config{
        .screen_width = kCanvasWidth,
        .screen_height = kCanvasHeight,
        .eye_spacing = static_cast<int>(kCanvasWidth * 0.55f),
        .eye_width = static_cast<int>(kCanvasWidth * 0.35f),
        .eye_height = static_cast<int>(kCanvasHeight * 0.55f),
        .blink_duration = 0.12f,
        .blink_interval = 4.0f,
        .enable_auto_blink = true,
        .enable_pupil_physics = true,
        .on_draw =
            [this](const espp::ExpressiveEyes::EyeState &left, const espp::ExpressiveEyes::EyeState &right) {
                RenderState &st = *g_state;
                st.draw(left, right);
                st.last_draw = Clock::now();
                st.last_left = left;
                st.last_right = right;
                st.have_last = true;
                canvas_dirty_ = true;
                redraws_.fetch_add(1, std::memory_order_relaxed);
            },
        .log_level = espp::Logger::Verbosity::WARN};
    s.eyes = std::make_unique<espp::ExpressiveEyes>(config);

    __android_log_print(ANDROID_LOG_INFO, kTag, "render thread ready: canvas %dx%d RGB565, LVGL %d.%d.%d",
                        kCanvasWidth, kCanvasHeight, LVGL_VERSION_MAJOR, LVGL_VERSION_MINOR,
                        LVGL_VERSION_PATCH);

    // Frames run only while a window is attached. The check happens before every sleep; a
    // attach_window() racing with it wakes the looper (ALooper_wake is sticky), so no attach is missed.
    while (true) {
        if (!frame_scheduled_ && has_window()) {
            last_frame_nanos_ = 0;  // resume without jumping the animation forward
            AChoreographer_postFrameCallback64(AChoreographer_getInstance(), on_frame, this);
            frame_scheduled_ = true;
        }
        ALooper_pollOnce(-1, nullptr, nullptr, nullptr);
    }
}

void EyeRenderer::on_frame(int64_t frame_time_nanos, void *self) {
    auto *renderer = static_cast<EyeRenderer *>(self);
    renderer->frame(frame_time_nanos);
    if (renderer->has_window()) {
        AChoreographer_postFrameCallback64(AChoreographer_getInstance(), on_frame, self);
    } else {
        renderer->frame_scheduled_ = false;  // suspended until the next attach_window()
    }
}

void EyeRenderer::frame(int64_t frame_time_nanos) {
    const auto work_start = Clock::now();

    float dt = 0.0f;
    if (last_frame_nanos_ != 0) {
        dt = std::clamp(static_cast<float>(frame_time_nanos - last_frame_nanos_) / 1e9f, 0.0f, kMaxFrameDt);
        record_interval((frame_time_nanos - last_frame_nanos_) / 1000);
    }
    last_frame_nanos_ = frame_time_nanos;

    apply_commands();
    advance_color_fade(dt);
    g_state->eyes->update(dt);  // may call the drawer, which sets canvas_dirty_
    RenderState &s = *g_state;
    if (s.shape == static_cast<int>(Shape::SLEEP) && s.have_last &&
        Clock::now() - s.last_draw >= std::chrono::milliseconds(100)) {
        s.draw(s.last_left, s.last_right);
        s.last_draw = Clock::now();
        canvas_dirty_ = true;
        redraws_.fetch_add(1, std::memory_order_relaxed);
    }
    present();

    const auto work_us = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - work_start).count());
    frames_.fetch_add(1, std::memory_order_relaxed);
    work_us_total_.fetch_add(work_us, std::memory_order_relaxed);
    if (work_us > work_us_max_.load(std::memory_order_relaxed)) {
        work_us_max_.store(work_us, std::memory_order_relaxed);
    }
}

void EyeRenderer::apply_commands() {
    std::deque<EyeCommand> pending;
    {
        std::lock_guard<std::mutex> lock(command_mutex_);
        pending.swap(commands_);
    }
    espp::ExpressiveEyes &eyes = *g_state->eyes;
    for (const EyeCommand &command : pending) {
        if (const auto *c = std::get_if<SetExpression>(&command)) {
            if (c->id >= static_cast<int>(espp::ExpressiveEyes::Expression::NEUTRAL) &&
                c->id <= static_cast<int>(espp::ExpressiveEyes::Expression::WINK_RIGHT)) {
                eyes.set_expression(static_cast<espp::ExpressiveEyes::Expression>(c->id));
            } else {
                __android_log_print(ANDROID_LOG_WARN, kTag, "ignored expression id %d", c->id);
            }
        } else if (const auto *l = std::get_if<LookAt>(&command)) {
            eyes.look_at(l->x, l->y);
        } else if (std::holds_alternative<Blink>(command)) {
            eyes.blink();
        } else if (const auto *sh = std::get_if<SetShape>(&command)) {
            if (sh->id >= static_cast<int>(Shape::ORIGINAL) &&
                sh->id <= static_cast<int>(Shape::CROSS)) {
                rebuild_drawer(sh->id, g_state->color);
            } else {
                __android_log_print(ANDROID_LOG_WARN, kTag, "ignored shape id %d", sh->id);
            }
        } else if (const auto *c = std::get_if<SetColor>(&command)) {
            RenderState &s = *g_state;
            s.fade_from = s.color;
            s.fade_to = c->rgb & 0xffffff;
            s.fade_s = static_cast<float>(std::clamp(c->fade_ms, 0, 5000)) / 1000.0f;
            s.fade_t = s.fade_s > 0.0f ? 0.0f : 1.0f;
            if (s.fade_t >= 1.0f) set_drawer_color(s.fade_to);
        }
    }
}

void EyeRenderer::advance_color_fade(float dt) {
    RenderState &s = *g_state;
    if (s.fade_t >= 1.0f) return;
    s.fade_t = std::min(1.0f, s.fade_t + (s.fade_s > 0.0f ? dt / s.fade_s : 1.0f));
    set_drawer_color(lerp_rgb(s.fade_from, s.fade_to, s.fade_t));
}

/// A color change keeps the current shape.
void EyeRenderer::set_drawer_color(uint32_t rgb) {
    RenderState &s = *g_state;
    if (s.drawer != nullptr && rgb == s.color) return;
    rebuild_drawer(s.shape, rgb);
}

/// Replaces the drawer with a new one for [shape] in color [rgb] and redraws the latest eye state
/// with it, so the change shows on the next present. The original Monochrome Blue drawer takes the
/// color through its own Config.color; later shapes are our own EyeDrawer implementations that
/// receive the same engine state.
void EyeRenderer::rebuild_drawer(int shape, uint32_t rgb) {
    RenderState &s = *g_state;
    std::unique_ptr<eye_drawer::EyeDrawer> drawer;
    switch (static_cast<Shape>(shape)) {
        case Shape::SLEEP:
            drawer = std::make_unique<eye_drawer::SleepDrawer>(
                eye_drawer::SleepDrawer::Config{.screen_width = kCanvasWidth,
                                                .screen_height = kCanvasHeight,
                                                .color = lv_color_hex(rgb),
                                                .canvas = s.canvas,
                                                .canvas_buffer = s.canvas_buffer,
                                                .lvgl_mutex = s.lvgl_mutex});
            break;
        case Shape::CROSS:
            drawer = std::make_unique<eye_drawer::CrossDrawer>(
                eye_drawer::CrossDrawer::Config{.screen_width = kCanvasWidth,
                                                .screen_height = kCanvasHeight,
                                                .color = lv_color_hex(rgb),
                                                .canvas = s.canvas,
                                                .canvas_buffer = s.canvas_buffer,
                                                .lvgl_mutex = s.lvgl_mutex});
            break;
        case Shape::ORIGINAL:
        default:
            drawer = std::make_unique<eye_drawer::MonochromeBlueDrawer>(
                eye_drawer::MonochromeBlueDrawer::Config{.screen_width = kCanvasWidth,
                                                         .screen_height = kCanvasHeight,
                                                         .color = lv_color_hex(rgb),
                                                         .canvas = s.canvas,
                                                         .canvas_buffer = s.canvas_buffer,
                                                         .lvgl_mutex = s.lvgl_mutex});
            shape = static_cast<int>(Shape::ORIGINAL);
            break;
    }
    s.draw = drawer->get_draw_callback();  // replace the callback before the old drawer goes away
    s.drawer = std::move(drawer);
    s.shape = shape;
    s.color = rgb;
    if (s.have_last) {
        s.draw(s.last_left, s.last_right);
        s.last_draw = Clock::now();
        canvas_dirty_ = true;
    }
}

void EyeRenderer::present() {
    std::lock_guard<std::mutex> lock(window_mutex_);
    if (window_ == nullptr) return;  // keep canvas_dirty_ so the next window gets a frame

    if (window_needs_geometry_) {
        // The compositor scales this buffer to the SurfaceView's (4:3) size. On failure the flag
        // stays set and the next frame retries.
        const int rc = ANativeWindow_setBuffersGeometry(window_, kCanvasWidth, kCanvasHeight,
                                                        WINDOW_FORMAT_RGB_565);
        if (rc != 0) {
            __android_log_print(ANDROID_LOG_WARN, kTag, "setBuffersGeometry failed: %d", rc);
            return;
        }
        window_needs_geometry_ = false;
        canvas_dirty_ = true;  // a new window always gets a full frame
    }
    if (!canvas_dirty_) return;

    ANativeWindow_Buffer out;
    if (ANativeWindow_lock(window_, &out, nullptr) != 0) return;  // keep dirty, retry next frame
    const bool format_ok = out.format == WINDOW_FORMAT_RGB_565;
    if (format_ok) {
        const lv_draw_buf_t *src = lv_canvas_get_draw_buf(g_state->canvas);
        const auto *src_row = static_cast<const uint8_t *>(src->data);
        auto *dst_row = static_cast<uint8_t *>(out.bits);
        const int rows = std::min(kCanvasHeight, static_cast<int>(out.height));
        const size_t row_bytes = static_cast<size_t>(std::min(kCanvasWidth, static_cast<int>(out.width))) * 2;
        const size_t dst_stride = static_cast<size_t>(out.stride) * 2;
        for (int y = 0; y < rows; ++y) {
            std::memcpy(dst_row, src_row, row_bytes);
            src_row += src->header.stride;
            dst_row += dst_stride;
        }
    } else if (!format_error_logged_) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "unexpected window format %d", out.format);
        format_error_logged_ = true;
    }
    const int posted = ANativeWindow_unlockAndPost(window_);
    if (format_ok && posted == 0) {
        canvas_dirty_ = false;
        presents_.fetch_add(1, std::memory_order_relaxed);
    }
}

}  // namespace chati
