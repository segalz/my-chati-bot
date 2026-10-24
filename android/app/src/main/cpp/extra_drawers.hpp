#pragma once

#include "eye_drawer.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <mutex>

namespace eye_drawer {

struct ExtraDrawerConfig {
    int screen_width;
    int screen_height;
    lv_color_t color = lv_color_hex(0x00BFFF);
    lv_obj_t *canvas;
    lv_color_t *canvas_buffer;
    std::recursive_mutex &lvgl_mutex;
};

namespace extra_detail {

inline void line(lv_layer_t &layer, lv_color_t color, float x1, float y1,
                 float x2, float y2, int width, lv_opa_t opacity = LV_OPA_COVER) {
    lv_draw_line_dsc_t dsc;
    lv_draw_line_dsc_init(&dsc);
    dsc.color = color;
    dsc.width = width;
    dsc.opa = opacity;
    dsc.round_start = 1;
    dsc.round_end = 1;
    dsc.p1.x = x1;
    dsc.p1.y = y1;
    dsc.p2.x = x2;
    dsc.p2.y = y2;
    lv_draw_line(&layer, &dsc);
}

}  // namespace extra_detail

class SleepDrawer final : public EyeDrawer {
public:
    using Config = ExtraDrawerConfig;

    explicit SleepDrawer(const Config &config) : config_(config) {}

    DrawCallback get_draw_callback() override {
        return [this](const espp::ExpressiveEyes::EyeState &left,
                      const espp::ExpressiveEyes::EyeState &right) {
            std::lock_guard<std::recursive_mutex> lock(config_.lvgl_mutex);
            lv_canvas_fill_bg(config_.canvas, lv_color_black(), LV_OPA_COVER);
            lv_layer_t layer;
            lv_canvas_init_layer(config_.canvas, &layer);
            draw_lid(layer, left);
            draw_lid(layer, right);
            draw_snoring(layer, right);
            lv_canvas_finish_layer(config_.canvas, &layer);
        };
    }

private:
    void draw_lid(lv_layer_t &layer, const espp::ExpressiveEyes::EyeState &eye) const {
        if (eye.width <= 0 || eye.height <= 0) return;
        const int thickness = std::min(eye.height, std::max(1, eye.width / 16));
        const float half_width = static_cast<float>(eye.width - thickness) * 0.5f;
        extra_detail::line(layer, config_.color, eye.x - half_width, eye.y,
                           eye.x + half_width, eye.y, thickness);
    }

    void draw_snoring(lv_layer_t &layer, const espp::ExpressiveEyes::EyeState &right) const {
        if (right.width <= 0) return;
        using Clock = std::chrono::steady_clock;
        // Shared across replacements so color fades do not restart the animation.
        static const auto epoch = Clock::now();
        const double seconds = std::chrono::duration<double>(Clock::now() - epoch).count();
        const float phase = static_cast<float>(std::fmod(seconds, 2.0) / 2.0);
        const auto opacity = static_cast<lv_opa_t>(
            std::lround(static_cast<float>(LV_OPA_COVER) * (1.0f - phase)));
        const float size = std::max(6.0f, static_cast<float>(right.width) * 0.12f);
        const float rise = static_cast<float>(config_.screen_height) * 0.04f * phase;
        for (int i = 0; i < 3; ++i) {
            const float glyph = size * (1.0f - static_cast<float>(i) * 0.18f);
            const float x = std::clamp(static_cast<float>(right.x) + size * i,
                                       0.0f, std::max(0.0f, config_.screen_width - glyph - 2.0f));
            const float y = static_cast<float>(right.y) - size * (2.0f + i) - rise;
            const int thickness = std::max(1, static_cast<int>(glyph / 6.0f));
            extra_detail::line(layer, config_.color, x, y, x + glyph, y, thickness, opacity);
            extra_detail::line(layer, config_.color, x + glyph, y, x, y + glyph,
                               thickness, opacity);
            extra_detail::line(layer, config_.color, x, y + glyph, x + glyph, y + glyph,
                               thickness, opacity);
        }
    }

    Config config_;
};

class CrossDrawer final : public EyeDrawer {
public:
    using Config = ExtraDrawerConfig;

    explicit CrossDrawer(const Config &config) : config_(config) {}

    DrawCallback get_draw_callback() override {
        return [this](const espp::ExpressiveEyes::EyeState &left,
                      const espp::ExpressiveEyes::EyeState &right) {
            std::lock_guard<std::recursive_mutex> lock(config_.lvgl_mutex);
            lv_canvas_fill_bg(config_.canvas, lv_color_black(), LV_OPA_COVER);
            lv_layer_t layer;
            lv_canvas_init_layer(config_.canvas, &layer);
            draw_cross(layer, left);
            draw_cross(layer, right);
            lv_canvas_finish_layer(config_.canvas, &layer);
        };
    }

private:
    void draw_cross(lv_layer_t &layer, const espp::ExpressiveEyes::EyeState &eye) const {
        if (eye.width <= 0 || eye.height <= 0) return;
        const int thickness = std::min(eye.height, std::max(1, eye.width / 10));
        const float half_width = static_cast<float>(eye.width - thickness) * 0.5f;
        const float half_height = static_cast<float>(eye.height - thickness) * 0.5f;
        extra_detail::line(layer, config_.color, eye.x - half_width, eye.y - half_height,
                           eye.x + half_width, eye.y + half_height, thickness);
        extra_detail::line(layer, config_.color, eye.x - half_width, eye.y + half_height,
                           eye.x + half_width, eye.y - half_height, thickness);
    }

    Config config_;
};

}  // namespace eye_drawer
