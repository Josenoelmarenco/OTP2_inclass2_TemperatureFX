#!/bin/sh
# Starts the JavaFX application.
#
#  * When DISPLAY is set the window is drawn on that X server. On macOS this
#    is XQuartz, reached with DISPLAY=host.docker.internal:0.
#  * When DISPLAY is empty the application runs on a virtual framebuffer
#    (Xvfb), which is what happens inside Kubernetes: the pod stays healthy
#    without needing any X server on the host.
set -e

if [ -n "$DISPLAY" ]; then
    echo "Using the X server at DISPLAY=$DISPLAY"
    exec mvn -q -B javafx:run
else
    echo "No DISPLAY set - starting a virtual framebuffer (Xvfb)"
    exec xvfb-run -a --server-args="-screen 0 1280x800x24" mvn -q -B javafx:run
fi
