#pragma once

#include <android/looper.h>
#include <android/native_window.h>

#include <atomic>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <thread>
#include <variant>

namespace chati {

/// Commands posted from any thread; applied on the render thread before the next update.
struct SetExpression { int id; };          // espp::ExpressiveEyes::Expression ordinal (0..8)
struct LookAt { float x; float y; };       // -1..1, clamped by the engine
struct Blink {};
using EyeCommand = std::variant<SetExpression, LookAt, Blink>;

/// Owns the original espp::ExpressiveEyes engine, LVGL and the Monochrome Blue drawer on one
/// dedicated thread (LVGL is built with LV_OS_NONE, so it must never be touched elsewhere).
/// Frames are paced by AChoreographer while a window is attached; whenever the engine redraws
/// the canvas, the RGB565 pixels are copied into that ANativeWindow. Without a window (app in
/// the background) the thread stops scheduling frames and sleeps until a window is attached.
class EyeRenderer {
public:
    static constexpr int kCanvasWidth = 480;   // 4:3, the upstream example's aspect ratio
    static constexpr int kCanvasHeight = 360;

    static EyeRenderer &instance();

    /// Starts the render thread once per process; later calls are no-ops.
    void start();

    /// Attaches a window (acquired here) or detaches with nullptr. Blocks until any frame that is
    /// writing to the previous window has finished, so it is safe to call from surfaceDestroyed.
    void set_window(ANativeWindow *window);

    void post(EyeCommand command);

    /// One-line counters for logging and evidence.
    std::string stats();

private:
    EyeRenderer() = default;

    void thread_main();
    static void on_frame(int64_t frame_time_nanos, void *self);
    void frame(int64_t frame_time_nanos);
    void apply_commands();
    void present();
    bool has_window();

    std::thread thread_;
    std::atomic<bool> started_{false};
    std::atomic<ALooper *> looper_{nullptr};  // render thread's looper, for wake-ups

    std::mutex command_mutex_;
    std::deque<EyeCommand> commands_;

    // Guards window_ and the copy into it.
    std::mutex window_mutex_;
    ANativeWindow *window_ = nullptr;
    bool window_needs_geometry_ = false;

    // Render-thread state.
    bool frame_scheduled_ = false;
    int64_t last_frame_nanos_ = 0;  // 0 = next frame starts the animation clock (dt = 0)
    bool canvas_dirty_ = false;
    bool format_error_logged_ = false;

    // Counters (written on the render thread, read by stats()).
    std::atomic<uint64_t> frames_{0};
    std::atomic<uint64_t> redraws_{0};
    std::atomic<uint64_t> presents_{0};
    std::atomic<uint64_t> work_us_total_{0};
    std::atomic<uint64_t> work_us_max_{0};
};

}  // namespace chati
