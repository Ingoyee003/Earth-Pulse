# Earth Pulse

**Autonomous Earth System Trend Discovery Platform for Android**  
Built with Kotlin, Jetpack Compose, Material Design 3, and authentic NASA Earth-observation time series.

[![Platform](https://img.shields.io/badge/Platform-Android%20(API%2024%E2%80%9336)-12686E?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-102D44?style=flat-square)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20M3-4D8B72?style=flat-square)](https://developer.android.com/compose)
[![NASA Data](https://img.shields.io/badge/Data-NASA%20POWER%20(MERRA--2%20%26%20CERES)-102D44?style=flat-square)](https://power.larc.nasa.gov)

---

## Overview

**Earth Pulse** turns multi-decadal NASA climate and radiative flux time series (1981–2024) into reproducible, statistically rigorous, and plain-language trend investigations directly on Android.

Rather than drawing misleading lines across missing data or overclaiming causality, Earth Pulse pairs **real NASA POWER data** (live REST API queries + verbatim bundled regional reference snapshots) with a deterministic non-parametric statistical engine (`TrendAnalyzer`) and an inspectable **Evidence, Provenance & Limitations Drawer** on every result.

> **Scientific & Non-Operational Notice:** Earth Pulse is a public-data exploratory and educational tool (aligned with *NASA Space Apps Challenge 2026*). It is **not** an operational weather warning, aviation, or clinical/regulatory forecasting system.

---

## Scientific Design Principles

- **Authentic NASA Data Only:** Every series comes either live from the NASA POWER API or from verbatim cached NASA POWER JSON snapshots bundled in `app/src/main/assets/nasa_cached/`. No synthetic or simulated curves are ever presented as NASA observations.
- **No Silent Gap Filling:** NASA `-999.0` fill values (e.g., pre-1984 CERES solar irradiance slots) and out-of-range measurements are marked as `MISSING` or `OUT_OF_RANGE` with `null` values—never replaced with `0.0` or silently interpolated.
- **Seasonality-Aware Inference:** Defaults to **Deseasonalized Anomalies** and the **Seasonal Mann–Kendall** test so annual winter–summer swings do not mask long-term shifts or inflate trend significance.
- **Honest Uncertainty:** Non-significant results explicitly state that the selected test and window do not provide sufficient evidence against no monotonic trend, rather than claiming "zero physical change."
- **Full Provenance & Caveats:** Every result records dataset version, spatial resolution, retrieval/snapshot timestamp, missingness rate, autocorrelation caveats, and official NASA documentation links.

---

## NASA Data Sources & Variables

All variables are queried from the **NASA Langley Research Center (LaRC) POWER Monthly Point API** (`https://power.larc.nasa.gov/api/temporal/monthly/point`, `community=AG`) and backed offline for curated regions by verbatim JSON snapshots (`1981–2024`).

| Parameter Code | Display Name | Unit | NASA Dataset / Model | Spatial Grid | Coverage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `T2M` | Air Temperature (2m) | `°C` | NASA POWER / MERRA-2 | `0.5° × 0.625°` | 1981–2024 |
| `TS` | Earth Skin Temperature | `°C` | NASA POWER / MERRA-2 | `0.5° × 0.625°` | 1981–2024 |
| `PRECTOTCORR` | Bias-Corrected Precipitation | `mm/day` | NASA POWER / MERRA-2 + IMERG | `0.5° × 0.625°` | 1981–2024 |
| `ALLSKY_SFC_SW_DWN` | Surface Solar Irradiance | `MJ/m²/day` | CERES SYN1deg Ed4.1 | `1.0° × 1.0°` | 1984–2024 |
| `RH2M` | Relative Humidity (2m) | `%` | NASA POWER / MERRA-2 | `0.5° × 0.625°` | 1981–2024 |
| `GWETTOP` | Surface Soil Wetness (`0–5 cm`) | `index (0–1)` | NASA POWER / MERRA-2 | `0.5° × 0.625°` | 1981–2024 |

*Note: Annual summary keys (`YYYY13`) returned by NASA POWER are excluded prior to monthly aggregation.*

---

## Study Regions & Global Coordinate Support

Users can investigate **6 curated NASA study regions** (with offline verbatim cache parity) or enter **any custom global latitude/longitude** (`-89.5°..89.5°`, `-179.5°..179.5°`) via interactive map selection or coordinate inputs for live NASA POWER queries.

| Region ID | Study Region | Area | Centroid (`Lat, Lon`) | Primary Earth-System Focus |
| :--- | :--- | :--- | :--- | :--- |
| `svalbard_arctic` | Svalbard Archipelago | Norway / High Arctic | `78.22°, 15.63°` | Arctic amplification & winter warming |
| `amazon_central` | Central Amazon Basin | Brazil | `-3.12°, -60.02°` | Tropical canopy heat & soil wetness |
| `sahel_west` | Western Sahel Corridor | Mali / Niger | `14.50°, -2.50°` | Semi-arid monsoon rainfall variability |
| `california_central_valley` | California Central Valley | United States | `36.74°, -119.79°` | Mediterranean agricultural water balance |
| `ganges_delta` | Ganges–Brahmaputra Delta | Bangladesh / India | `23.68°, 90.35°` | Monsoon cycle & humidity dynamics |
| `murray_darling` | Murray–Darling Basin | Australia | `-34.18°, 142.16°` | ENSO/IOD-linked hydroclimate variability |

---

## Statistical Trend Engine (`TrendAnalyzer`)

Implemented cleanly in pure Kotlin (`earthpulse-stats-1.0.0`) with deterministic unit-tested math:

- **Quality Control:** Filters to the selected year range, rejects `<= -900.0` NASA fill sentinels and out-of-bound values as gaps (`value = null`), and enforces minimum valid sample guards (`>= 12` monthly observations or `>= 6` annual observations; `<= 45%` missingness) before computing a trend.
- **Temporal Aggregations:**
  - `Monthly` (raw monthly values)
  - `Deseasonalized Anomaly` (calendar-month baseline subtraction across the study window)
  - `Annual Mean` (computed only for years with `>= 9` valid months)
- **Slope Estimators:**
  - **Theil–Sen Slope** (median of pairwise slopes, scaled to *per decade*) with **95% Confidence Interval**
  - **OLS Linear Regression** (slope per decade + $R^2$)
- **Significance & Diagnostics:**
  - **Seasonal Mann–Kendall** (strata-wise within-month comparisons) and **Standard Mann–Kendall** ($Z$-score, Kendall's $\tau$, two-sided $p$-value evaluated at $\alpha = 0.05$ or $\alpha = 0.01$)
  - **Lag-1 Autocorrelation** diagnostic flag (`hasAutocorrelationCaveat`)
  - **Bivariate Concurrent Correlation** (Pearson $r$ over overlapping valid months only when comparing 2 variables, explicitly framed as non-causal)

---

## Features & Screens

1. **Overview (`OverviewScreen`)**: Hero summary, data-integrity pillars, and 3 one-tap **Featured Investigations** (*Arctic Amplification in Svalbard*, *Central Amazon Heat & Soil Wetness*, *Western Sahel Monsoon Rainfall & Humidity*).
2. **Investigate (`InvestigateScreen`)**: 4-step guided workflow (*Location → Variables → Period → Review*) featuring an interactive custom Jetpack Compose `RegionMap` canvas, custom global coordinate entry, optional Variable Advisor helper, variable coverage constraints, and collapsible **Advanced Analysis Settings**.
3. **Results (`ResultsScreen`)**: Executive finding cards, custom Compose Canvas time-series + regression line charts (`EarthPulseCharts`) with visible missing-data gap markers, optional side-by-side secondary variable or comparison region correlation, and the full **Evidence, Provenance & Limitations Drawer**.
4. **Discoveries (`DiscoveriesScreen`)**: Local Room-persisted library of saved investigations with search/filtering, JSON-serialized snapshot restoration, and CSV summary export/sharing.
5. **About & Data (`AboutDataScreen`)**: Full integrated NASA dataset catalogue, quality/fill rules, limitations, external NASA methodology links, and a 9-term **Scientific Glossary**.
6. **Adaptive UI**: Edge-to-edge Material 3 layout with `NavigationBar` on compact screens and `NavigationRail` on medium/expanded screens, paired with custom bundled `Inter` and `JetBrains Mono` fonts and a custom adaptive launcher icon.

---

## Architecture & Project Structure

Follows **MVVM** with unidirectional `StateFlow` updates, Jetpack Navigation Compose, Room persistence, and Retrofit/Moshi networking:

```text
app/src/main/
├── assets/nasa_cached/                 # 6 verbatim NASA POWER monthly JSON snapshots (1981–2024)
├── res/
│   ├── font/                           # Bundled Inter & JetBrains Mono .ttf typography
│   └── drawable/, mipmap-*/            # Custom adaptive Earth Pulse launcher icon assets
└── java/com/example/
    ├── MainActivity.kt                 # Edge-to-edge host, adaptive NavBar/NavRail, NavHost
    ├── data/
    │   ├── ai/AiRepository.kt          # PlaceholderAiRepository (deterministic variable/summary helper)
    │   ├── catalog/NasaCatalog.kt      # NASA datasets, 6 variables, 6 regions, demos, glossary
    │   ├── local/
    │   │   ├── EarthPulseDatabase.kt   # Room DB, DAO & SavedDiscoveryEntity
    │   │   └── VerifiedNasaArchive.kt  # Loads & parses verbatim NASA POWER JSON assets
    │   ├── model/EarthPulseModels.kt   # Domain models, QC enums, provenance & result classes
    │   ├── remote/NasaPowerApiService.kt # Retrofit + Moshi client for power.larc.nasa.gov
    │   └── repository/InvestigationRepository.kt # Live-with-cache fallback & Room persistence
    ├── domain/analysis/
    │   └── TrendAnalyzer.kt            # Deterministic QC, aggregation, Sen's slope, MK/SMK, Pearson r
    └── ui/
        ├── components/                 # Reusable cards, EvidenceDrawer, RegionMapCanvas, EarthPulseCharts
        ├── screens/                    # Overview, Investigate, Results, Discoveries, AboutData
        ├── theme/                      # Material 3 Light/Dark ColorScheme, Typography, Shapes
        └── viewmodel/EarthPulseViewModel.kt

app/src/test/java/com/example/
├── TrendAnalyzerTest.kt                # Unit tests for QC, Sen's slope, OLS, MK/SMK, autocorrelation
├── ExampleRobolectricTest.kt           # Compose UI & real bundled NASA POWER JSON asset tests
└── ExampleUnitTest.kt
```

---

## Tech Stack

| Category | Library / Version |
| :--- | :--- |
| **Language & Build** | Kotlin `2.2.10`, Android Gradle Plugin `9.1.1`, KSP `2.3.5` |
| **SDK Targets** | `minSdk 24`, `targetSdk 36`, `compileSdk 36.1` |
| **UI & Navigation** | Jetpack Compose BOM `2024.09.00`, Material 3, Navigation Compose `2.8.9`, Custom Canvas Charts |
| **Concurrency & Lifecycle** | Kotlin Coroutines `1.10.2`, Lifecycle ViewModel & Runtime Compose `2.8.7` |
| **Networking** | Retrofit `2.12.0`, OkHttp `4.10.0`, Moshi `1.15.2` (KSP codegen) |
| **Persistence** | AndroidX Room `2.7.0` (with KSP compiler) |
| **Testing** | JUnit `4.13.2`, Robolectric `4.16.1`, Compose UI Test JUnit4, Coroutines Test |

---

## Getting Started

### Prerequisites

- **JDK 11+** (or Android Studio Meerkat / Narwhal+ with Android SDK 36 installed)
- Android SDK Platform 36 (`ANDROID_SDK_ROOT` / `ANDROID_HOME` configured)

### Build & Run

1. Open the project in Android Studio or run from the command line using `gradle` (ensure the Android SDK environment variable is set):

   ```bash
   gradle :app:assembleDebug
   ```

2. Run the full JVM unit and Robolectric test suite (no emulator required):

   ```bash
   gradle :app:testDebugUnitTest
   ```

3. Install and launch the debug APK on a connected Android device or emulator (`API 24+`).

### Optional AI Helper Status

The optional **Variable Advisor** and plain-language explanation cards currently run via `PlaceholderAiRepository` in **deterministic placeholder preview mode** (no cloud LLM keys or Firebase configuration required to build or run). NASA data retrieval and all statistical computations execute directly against NASA POWER and on-device Kotlin math.

---

## Data Attribution

- **NASA POWER Project:** Data obtained from the NASA Langley Research Center (LaRC) POWER Project funded through the NASA Earth Science/Applied Science Program ([https://power.larc.nasa.gov/](https://power.larc.nasa.gov/)).
- **CERES SYN1deg:** NASA Langley Atmospheric Science Data Center CERES Science Team ([https://ceres.larc.nasa.gov/data/](https://ceres.larc.nasa.gov/data/)).


#Developer details:
Mahmuda Islam Rifah 
mahmudaislamrifa@gmail.com

#Consider to give a star if you like the project or find it useful. 
