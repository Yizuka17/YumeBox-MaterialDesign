cmake_minimum_required(VERSION 3.22.1)

project(mihomo-shell C)

add_executable(mihomo-shell shell.c)
set_target_properties(mihomo-shell PROPERTIES OUTPUT_NAME "libmihomo.so")
add_executable(mihomo-preview-shell shell.c)
set_target_properties(mihomo-preview-shell PROPERTIES OUTPUT_NAME "libpreview.so")

foreach(shell_target IN ITEMS mihomo-shell mihomo-preview-shell)
    target_compile_features(${shell_target} PRIVATE c_std_17)
    target_compile_options(${shell_target} PRIVATE -O3 -fPIE -fvisibility=hidden -Wall -Wextra -Werror)
    target_link_options(
        ${shell_target}
        PRIVATE
        -pie
        -Wl,--gc-sections
        -Wl,--build-id=none
        -Wl,-z,relro
        -Wl,-z,now
    )
    target_link_libraries(${shell_target} PRIVATE dl)
endforeach()
