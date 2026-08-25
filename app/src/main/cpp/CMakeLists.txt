cmake_minimum_required(VERSION 3.22)

project(pal_llama LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# No linker page-size option here; older NDK lld rejects '--page-size'.
# A post-build step will patch built ELF PT_LOAD p_align values to 16 KB.

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp source tree not found at ${LLAMA_SRC}")
endif()

set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SRC} ${CMAKE_CURRENT_BINARY_DIR}/llama-build)

add_library(pal_llama SHARED
    pal_llama.cpp
)

# Ensure the pal_llama target itself also receives the page-size linker option
# (redundant with the global setting but helps with toolchains that ignore
# CMAKE_SHARED_LINKER_FLAGS for subprojects).
if (CMAKE_VERSION VERSION_GREATER "3.12")
    target_link_options(pal_llama PRIVATE ${PAGE_SIZE_ARG})
endif()

target_include_directories(pal_llama PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/ggml/src
)

target_link_libraries(pal_llama PRIVATE
    llama
    llama-common
    log
    android
)
