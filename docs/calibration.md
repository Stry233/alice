# Calibration

Each lens has a unique relationship between focus distance and motor position. Calibration teaches Alice this mapping for your specific lens.

## Why Calibrate

The autofocus pipeline works by: measure depth (meters) -> look up motor position -> move motor. The depth sensor is universal, but the motor-to-focus mapping is lens-specific. A 50mm f/1.4 at 2 meters might need motor position 1800, while an 85mm f/1.8 at the same distance needs position 2400. Calibration captures these data points so Alice can interpolate between them.

## Recording Points

### On Android

1. Go to Settings > Autofocus > Calibrator
2. Position a subject at a known distance
3. Manually adjust the motor until the subject is in sharp focus
4. Wait for the depth reading to stabilize (confidence > 70%)
5. Tap **Record Point**
6. Repeat for at least 3 points across your focus range

### On Alice Studio

1. Switch to CFG > Calibration
2. Use the motor slider (left panel) to adjust focus
3. Watch the depth + confidence readout in the depth panel
4. When depth is stable and the image is sharp, click **Record Point**
5. The recorded-points table (center) and interpolation graph (right) update live

## Recommended Points

Record at least 3 points. More points improve interpolation accuracy. Suggested distances:

| Point | Approximate distance |
|:------|:--------------------|
| 1 | Minimum focus distance of the lens |
| 2 | 1 meter |
| 3 | 2 meters |
| 4 | 5 meters |
| 5 | Near infinity (far wall, horizon) |

Spread points evenly across the motor range. If you notice focus accuracy drops at a particular distance, add an extra point there.

## Exporting

After recording, click **Export Mapping** and save as a JSON file. The file contains:

```json
{
  "version": "1.0",
  "name": "Canon_50mm_f1.4",
  "description": "Exported from Alice Studio",
  "mappingPoints": [
    { "depth": 0.45, "motorPosition": 800, "confidence": 0.92 },
    { "depth": 1.02, "motorPosition": 1650, "confidence": 0.95 },
    { "depth": 2.15, "motorPosition": 2200, "confidence": 0.88 },
    { "depth": 5.30, "motorPosition": 3100, "confidence": 0.82 }
  ],
  "metadata": {
    "calibrationMethod": "manual",
    "createdAt": 1713312000
  }
}
```

## Loading a Calibration

### From a Preset

Alice ships with built-in presets (Linear, Logarithmic, Portrait, Landscape, Macro) for quick testing. Select one from the preset dropdown in OPS mode (left sidebar) or CFG > Calibration.

### From a File

Click "From file..." in the preset dropdown (OPS mode) or use the file dialog in the calibration tab. Accepts the same JSON format shown above.

## Tips

- **Calibrate in the environment you'll shoot in.** Depth accuracy varies with lighting — a calibration done in bright daylight may drift in dim interiors.
- **Non-parfocal lenses need per-focal-length profiles.** If your zoom lens shifts focus when zooming, create a separate calibration for each focal length you use.
- **Name your calibrations clearly.** Include the lens model, focal length, and any special conditions (e.g., `Sigma_35mm_f1.4_indoor`).
- **Recalibrate periodically.** Mechanical backlash and sensor drift can accumulate over long shoots.
