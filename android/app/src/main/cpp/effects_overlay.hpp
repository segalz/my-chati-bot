#pragma once

#include "expressive_eyes.hpp"
#include "lvgl.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>

namespace chati {

/** Render-thread-only overlay. Rasterizes vector primitives into the existing RGB565 canvas;
 * no LVGL draw tasks, temporary layers, images, fonts, or heap allocations per frame.
 * Caller holds the same recursive LVGL mutex used by the eye drawer.
 */
class EffectsOverlay {
public:
    enum : uint32_t {
        SWEAT = 1u << 0, ANGER_MARK = 1u << 1, SPARKLES = 1u << 2,
        QUESTION = 1u << 3, EXCLAIM = 1u << 4, NOTES = 1u << 5, RAIN = 1u << 6,
        ALL = (1u << 7) - 1,
    };
    using Clock = std::chrono::steady_clock;

    bool set_mask(uint32_t mask) {
        mask &= ALL;
        if (mask == mask_) return false;
        const auto now = Clock::now();
        for (size_t i = 0; i < started_.size(); ++i) {
            if ((mask & (1u << i)) && !(mask_ & (1u << i))) started_[i] = now;
        }
        mask_ = mask;
        return true;
    }
    uint32_t mask() const { return mask_; }

    void draw(lv_obj_t *canvas, const espp::ExpressiveEyes::EyeState &left,
              const espp::ExpressiveEyes::EyeState &right, uint32_t eye_color) const {
        if (!mask_) return;
        lv_draw_buf_t *buf = lv_canvas_get_draw_buf(canvas);
        Pixels pixels{*buf};
        const auto now = Clock::now();
        const auto phase = [&](size_t bit, float period) {
            return std::fmod(std::chrono::duration<float>(now - started_[bit]).count(), period) / period;
        };
        const float w = std::max(40.0f, static_cast<float>(right.width));
        const float size = std::clamp(w * 0.12f, 8.0f, 20.0f);
        const float top = std::min(left.y - left.height * 0.5f, right.y - right.height * 0.5f);
        const float above = std::clamp(top - size * 1.5f, size * 1.6f,
                                       static_cast<float>(buf->header.h) - size * 3.0f);
        const float outer = std::clamp(right.x + w * 0.5f + size * 0.6f, size,
                                       static_cast<float>(buf->header.w) - size);
        const float mid = (left.x + right.x) * 0.5f;
        if (mask_ & SWEAT) {
            const float y = above + size + phase(0, 2.8f) * size * 3;
            pixels.triangle(outer, y - size, outer - size * 0.4f, y,
                            outer + size * 0.4f, y, 0x80DFFF);
            pixels.disc(outer, y, size * 0.4f, 0x80DFFF);
        }
        if (mask_ & ANGER_MARK) {
            const float r = size * (0.7f + 0.18f * std::sin(phase(1, 0.9f) * kTau));
            for (int sx : {-1, 1}) for (int sy : {-1, 1}) {
                const float x = outer + sx * r * 0.25f;
                const float y = above + sy * r * 0.25f;
                pixels.line(x + sx * r, y, x, y, 3, 0xFF5050);
                pixels.line(x, y, x, y + sy * r, 3, 0xFF5050);
            }
        }
        if (mask_ & SPARKLES) {
            for (int i = 0; i < 4; ++i) {
                const auto &eye = i < 2 ? left : right;
                const float pulse = 0.5f + 0.5f * std::sin((phase(2, 1.7f) + i * 0.27f) * kTau);
                const float r = size * (0.3f + pulse * 0.5f);
                const float x = std::clamp(eye.x + (i % 2 ? 0.4f : -0.4f) * eye.width,
                                           size, static_cast<float>(buf->header.w) - size);
                const float y = i % 2 ? std::min(static_cast<float>(buf->header.h) - size,
                                                eye.y + eye.height * 0.5f + size) : above;
                const uint32_t color = i % 2 ? 0xFFFFFF : 0xFFE66D;
                const float alpha = 0.35f + pulse * 0.65f;
                pixels.triangle(x, y-r, x-r*0.3f, y, x+r*0.3f, y, color, alpha);
                pixels.triangle(x, y+r, x-r*0.3f, y, x+r*0.3f, y, color, alpha);
                pixels.triangle(x-r, y, x, y-r*0.3f, x, y+r*0.3f, color, alpha);
                pixels.triangle(x+r, y, x, y-r*0.3f, x, y+r*0.3f, color, alpha);
            }
        }
        if (mask_ & QUESTION) {
            const float y = above + std::sin(phase(3, 2.2f) * kTau) * size * 0.2f;
            // A polygonal hook and dot: no font dependency.
            pixels.line(mid-size*0.4f, y-size*0.5f, mid-size*0.2f, y-size, 3, 0xFFFFFF);
            pixels.line(mid-size*0.2f, y-size, mid+size*0.4f, y-size, 3, 0xFFFFFF);
            pixels.line(mid+size*0.4f, y-size, mid+size*0.5f, y-size*0.4f, 3, 0xFFFFFF);
            pixels.line(mid+size*0.5f, y-size*0.4f, mid, y, 3, 0xFFFFFF);
            pixels.line(mid, y, mid, y+size*0.3f, 3, 0xFFFFFF);
            pixels.disc(mid, y+size*0.8f, 2, 0xFFFFFF);
        }
        if (mask_ & EXCLAIM) {
            const float age = std::chrono::duration<float>(now - started_[4]).count();
            const float pop = age < 0.25f ? 1.0f + 0.3f * std::sin(age / 0.25f * kTau * 0.5f) : 1.0f;
            pixels.line(mid, above-size*pop, mid, above+size*0.25f*pop, 4, 0xFFFFFF);
            pixels.disc(mid, above+size*0.8f*pop, 2.5f, 0xFFFFFF);
        }
        if (mask_ & NOTES) {
            for (int i = 0; i < 2; ++i) {
                const float t = std::fmod(phase(5, 2.6f) + i * 0.5f, 1.0f);
                const float x = std::clamp(left.x - left.width*0.5f - size*(0.5f+i),
                                           size, static_cast<float>(buf->header.w) - size);
                const float y = left.y - size * (0.5f + t * 3);
                pixels.disc(x, y, size*0.27f, eye_color, 1-t);
                pixels.line(x+size*0.2f, y, x+size*0.2f, y-size, 2, eye_color, 1-t);
                pixels.line(x+size*0.2f, y-size, x+size*0.7f, y-size*0.7f, 2, eye_color, 1-t);
            }
        }
        if (mask_ & RAIN) {
            pixels.line(mid-size, above, mid+size, above, static_cast<int>(size), 0xAAAAAA);
            pixels.disc(mid-size*0.5f, above-size*0.3f, size*0.6f, 0xAAAAAA);
            pixels.disc(mid+size*0.3f, above-size*0.5f, size*0.7f, 0xAAAAAA);
            for (int i = 0; i < 4; ++i) {
                const float t = std::fmod(phase(6, 0.8f) + i*0.23f, 1.0f);
                const float x = mid + (i-1.5f)*size*0.65f;
                const float y = above + size*(0.7f+t*1.5f);
                pixels.line(x, y, x-size*0.15f, y+size*0.35f, 2, 0x65BFFF, 1-t*0.6f);
            }
        }
    }

private:
    static constexpr float kTau = 6.28318530718f;
    uint32_t mask_ = 0;
    std::array<Clock::time_point, 7> started_{};

    struct Pixels {
        lv_draw_buf_t &buf;
        void put(int x, int y, uint32_t rgb, float alpha) const {
            if (x < 0 || y < 0 || x >= static_cast<int>(buf.header.w) ||
                y >= static_cast<int>(buf.header.h)) return;
            auto *row = reinterpret_cast<uint16_t *>(static_cast<uint8_t *>(buf.data) + y*buf.header.stride);
            const uint16_t old = row[x];
            const auto blend = [alpha](uint32_t src, uint32_t dst) {
                return static_cast<uint32_t>(std::lround(src*alpha + dst*(1-alpha)));
            };
            const uint32_t r = blend((rgb >> 19) & 31, (old >> 11) & 31);
            const uint32_t g = blend((rgb >> 10) & 63, (old >> 5) & 63);
            const uint32_t b = blend((rgb >> 3) & 31, old & 31);
            row[x] = static_cast<uint16_t>((r << 11) | (g << 5) | b);
        }
        void disc(float cx, float cy, float r, uint32_t color, float alpha = 1) const {
            for (int y = static_cast<int>(std::floor(cy-r)); y <= static_cast<int>(std::ceil(cy+r)); ++y)
                for (int x = static_cast<int>(std::floor(cx-r)); x <= static_cast<int>(std::ceil(cx+r)); ++x)
                    if ((x-cx)*(x-cx)+(y-cy)*(y-cy) <= r*r) put(x, y, color, alpha);
        }
        void line(float x1, float y1, float x2, float y2, int width, uint32_t color, float alpha = 1) const {
            const int steps = std::max(1, static_cast<int>(std::ceil(std::max(std::abs(x2-x1), std::abs(y2-y1)))));
            for (int i = 0; i <= steps; ++i) {
                const float t = static_cast<float>(i)/steps;
                disc(x1+(x2-x1)*t, y1+(y2-y1)*t, width*0.5f, color, alpha);
            }
        }
        void triangle(float ax, float ay, float bx, float by, float cx, float cy,
                      uint32_t color, float alpha = 1) const {
            const auto cross = [](float x1, float y1, float x2, float y2, float x, float y) {
                return (x-x2)*(y1-y2)-(x1-x2)*(y-y2);
            };
            const int min_x = std::max(0, static_cast<int>(std::floor(std::min({ax,bx,cx}))));
            const int max_x = std::min(static_cast<int>(buf.header.w)-1, static_cast<int>(std::ceil(std::max({ax,bx,cx}))));
            const int min_y = std::max(0, static_cast<int>(std::floor(std::min({ay,by,cy}))));
            const int max_y = std::min(static_cast<int>(buf.header.h)-1, static_cast<int>(std::ceil(std::max({ay,by,cy}))));
            for (int y = min_y; y <= max_y; ++y) for (int x = min_x; x <= max_x; ++x) {
                const float a = cross(ax,ay,bx,by,x,y), b = cross(bx,by,cx,cy,x,y), c = cross(cx,cy,ax,ay,x,y);
                if (!((a<0 || b<0 || c<0) && (a>0 || b>0 || c>0))) put(x,y,color,alpha);
            }
        }
    };
};

}  // namespace chati
