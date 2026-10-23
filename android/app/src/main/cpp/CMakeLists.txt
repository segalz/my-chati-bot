cmake_minimum_required(VERSION 3.22)
project(chati_eyes LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(REPO_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../..)
set(THIRD_PARTY ${REPO_ROOT}/third_party)
set(EYES_COMPONENT ${THIRD_PARTY}/expressive_eyes-1.3.9/component)
set(ESPP ${THIRD_PARTY}/espp-v1.3.9/components)

# --- LVGL at the commit pinned by espp v1.3.9 (submodule), with our lv_conf.h -----------------
set(LV_BUILD_CONF_PATH ${CMAKE_CURRENT_SOURCE_DIR}/config/lv_conf.h CACHE PATH "" FORCE)
set(CONFIG_LV_BUILD_DEMOS OFF CACHE BOOL "" FORCE)
set(CONFIG_LV_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(CONFIG_LV_USE_THORVG_INTERNAL OFF CACHE BOOL "" FORCE)
set(LV_USE_FIND_PACKAGE OFF CACHE BOOL "" FORCE)
set(LV_USE_PKG_CONFIG OFF CACHE BOOL "" FORCE)
set(LV_FETCH_DEPENDENCIES OFF CACHE BOOL "" FORCE)
add_subdirectory(${THIRD_PARTY}/lvgl ${CMAKE_CURRENT_BINARY_DIR}/lvgl)

# --- Original espp sources, compiled unmodified (no -Werror on third-party code) ---------------
add_library(espp_eyes STATIC
    ${EYES_COMPONENT}/src/expressive_eyes.cpp
    ${ESPP}/logger/src/logger.cpp)
# SYSTEM: our -Werror must not apply to warnings inside unmodified upstream headers
# (e.g. the drawer's unused canvas_buffer_ field, noted in the port audit).
target_include_directories(espp_eyes SYSTEM PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/platform          # esp_heap_caps.h shim
    ${EYES_COMPONENT}/include
    ${EYES_COMPONENT}/example/main                 # eye_drawer.hpp, monochrome_blue_drawer.hpp
    ${ESPP}/base_component/include
    ${ESPP}/logger/include
    ${ESPP}/format/include
    ${THIRD_PARTY}/fmt/include)
target_link_libraries(espp_eyes PUBLIC lvgl::lvgl)

# --- Our Android host -------------------------------------------------------------------------
add_library(chati_eyes SHARED native_bridge.cpp eye_renderer.cpp)
target_compile_options(chati_eyes PRIVATE -Wall -Wextra -Werror)
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(chati_eyes PRIVATE espp_eyes ${android-lib} ${log-lib})
