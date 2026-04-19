# Attribution

Alice ships with a small number of third-party assets and models. Each
item below is compatibly licensed with Alice's own MIT license.

## Fonts

Bundled under `src/desktop/assets/fonts/`.

| Font | Weights | License | Source |
|---|---|---|---|
| Inter | Regular, Medium, SemiBold, Bold | SIL Open Font License 1.1 | https://rsms.me/inter/ |
| Roboto Mono | Regular, Medium | Apache License 2.0 | https://fonts.google.com/specimen/Roboto+Mono |
| Source Code Pro | Regular | SIL Open Font License 1.1 | https://fonts.adobe.com/fonts/source-code-pro |

Font files are loaded from the application bundle so they do not
collide with any system-installed copies.

## Models

Bundled under `src/desktop/assets/models/` and
`src/android/app/src/main/assets/`.

| Model | File | License | Source |
|---|---|---|---|
| YOLO11s-face | `yolov11s-face.onnx` | AGPL-3.0 (weights) / Apache-2.0 (architecture) | https://github.com/akanametov/yolo-face |
| YOLO-face (legacy) | `yolo-face.onnx` | AGPL-3.0 (weights) / Apache-2.0 (architecture) | https://github.com/akanametov/yolo-face |

The face-detection models are used for on-device inference only; no
image data leaves the device.

## Prior work

The autofocus / motor-control concept builds on the Tilta Nucleus-N
family of lens-control products. None of Tilta's firmware or
proprietary assets are used; the protocol implementation here is an
independent reimplementation against the public BLE characteristic
layout.
