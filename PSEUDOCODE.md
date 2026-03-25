# PSEUDOCODE — TLBAgent & TLBTerritory

---

## TLBAgent

### Constructor — `TLBAgent(...)`
Initializes a new TLB agent with its spatial identity, life-stage parameters, and empty life-history tracking maps.
All geographic coordinates are converted from geographic (longitude/latitude) space into the vegetation raster grid and then further scaled into the smaller UI display grid.
Spawn count is randomized within `[0, mpTlbSpawn]` and tamarisk patch quality is read immediately from the shared `tamariskInfo` lookup.
```
GIVEN agentID, stage, longitude, latitude, patchID, terrID, age, generation

  // Identity & stage
  SET tlbAgentID  = agentID
  SET tlbStage    = stage
  SET tlbAge      = age
  SET tlbGen      = generation
  SET tlbDiapause = false
  SET tlbCDL      = 0
  SET tlbMort     = state.mpTlbMort

  // Coordinate conversion chain
  SET tlbLonX = longitude
  SET tlbLatY = latitude
  COMPUTE vegGridX = longitudeXtoGridX(longitude, xllcornerVeg, vegCellSize)
  COMPUTE vegGridY = latitudeYtoGridY(latitude,  yllcornerVeg, vegCellSize, nRowsVeg)
  COMPUTE displayX = vegGridX * agentGrid.width  / vegetationRaster.width
  COMPUTE displayY = vegGridY * agentGrid.height / vegetationRaster.height
  SET displayLocation = Int2D(displayX, displayY)

  // Patch & territory linkage
  SET patchID  = patchID
  SET terrID   = terrID
  READ currentPTamarisk from tamariskInfo[patchID].pTamarisk

  // Reproduction
  SET tlbSpawn = random integer in [0, mpTlbSpawn)

  // Life-history data structures
  INITIALIZE dateData     = empty HashMap<String, Long>
  INITIALIZE locationData = empty HashMap<String, Double>
  INITIALIZE stageData    = empty HashMap<String, Enum>
  INITIALIZE ageData      = empty HashMap<String, Integer>
  SET actionExecuted = "null"

  PRINT debug info (ID, lon/lat, grid coords, display coords, patchID)
```

---

### `step(SimState state)`
The main weekly tick for each agent. It determines the current life stage from the agent's age, resets the generation counter at the start of each year, delegates all behavioral logic to `takeAction()`, and then logs the agent's state before incrementing age by one week.
If the agent's age reaches 8 or above it has exceeded its maximum lifespan and `death()` is called immediately before any other logic runs.
```
CAST state to TLBEnvironment eState
GET currentStep from eState.schedule.getSteps()
LOG "Agent <id> started step <currentStep>" to debugWriter and console

// Stage determination by age
IF      tlbAge == 0          → SET tlbStage = TLBEGG
ELSE IF tlbAge in [1, 3]     → SET tlbStage = TLBLARVA
ELSE IF tlbAge == 4          → SET tlbStage = TLBPUPA
ELSE IF tlbAge in [5, 7]     → SET tlbStage = TLBADULT
ELSE (tlbAge >= 8)           → CALL death(eState); RETURN   // max lifespan exceeded

LOG current stage, coordinates, patchID to debugWriter

// Annual generation reset
IF eState.currentWeek == 0:
  SET tlbGen = 0

// Behavior dispatch
CALL takeAction(tlbStage, eState)

// Step logging
FORMAT log string: step, week, year, agentID, stage, age, lon, lat,
                   vegGridX, vegGridY, displayX, displayY, patchID, actionExecuted
WRITE log string to logWriter
RESET actionExecuted = "null"

// Age progression
INCREMENT tlbAge by 1
```

---

### `takeAction(Stage, TLBEnvironment)`
Dispatches the correct behavior for the agent's current life stage using a switch statement.
Each stage first applies its mortality checks (diapause, random mortality, ant predation) before proceeding to any productive action such as feeding or dispersal.
Adults are the only stage that can disperse, lay eggs, or make decisions based on tamarisk quality.
```
SWITCH on tlbStage:

  // ── ADULT ────────────────────────────────────────────────
  CASE TLBADULT:
    // Diapause check
    IF checkDiapause(state) == true:
      DRAW random boolean with probability 0.1
      IF true  → LOG "diapause death"; INCREMENT numDeath; CALL death(); RETURN
      IF false → LOG "diapause survived"; RETURN  // skip all actions this tick

    // Active-season mortality
    SET this.tlbMort = state.mpTlbMort
    DRAW random boolean with probability tlbMort
    IF true → LOG "adult random death"; INCREMENT numDeath; CALL death(); RETURN

    // Behavior based on tamarisk quality at current patch
    IF   currentPTamarisk == 0 → CALL dispersal()          // no food: move
    ELIF currentPTamarisk == 1 → CALL feed_colonizeACell()  // adequate food: feed only
    ELIF currentPTamarisk == 2 → CALL feed_colonizeACell()  // good food: feed and reproduce
                                  CALL laying(state, this)
    ELSE → LOG "unknown tamarisk quality; no action taken"

  // ── EGG ──────────────────────────────────────────────────
  CASE TLBEGG:
    // Combined diapause + ant predation mortality
    SET this.tlbMort = state.mpTlbMort + state.mpAntPredation * 27.3
    IF checkDiapause(state) OR random(tlbMort):
      LOG "EGG: mortality"; INCREMENT numDeath; CALL death(); RETURN
    LOG "EGG: survived"   // will advance to LARVA via age increment

  // ── LARVA ─────────────────────────────────────────────────
  CASE TLBLARVA:
    // Combined diapause + ant predation mortality
    SET this.tlbMort = state.mpTlbMort + state.mpAntPredation * 35
    IF checkDiapause(state) OR random(tlbMort):
      LOG "LARVA: mortality"; INCREMENT numDeath; CALL death(); RETURN

    // Vegetation quality check
    IF currentPTamarisk == 0:
      LOG "LARVA: poor tamarisk patch death"; INCREMENT numDeath; CALL death(); RETURN
    ELSE:
      LOG "LARVA: feed"; CALL feed_colonizeACell()

  // ── PUPA ──────────────────────────────────────────────────
  CASE TLBPUPA:
    // Combined diapause + ant predation mortality
    SET this.tlbMort = state.mpTlbMort + state.mpAntPredation * 18.5
    IF checkDiapause(state) OR random(tlbMort):
      LOG "PUPA: mortality"; INCREMENT numDeath; CALL death(); RETURN
    LOG "PUPA: survived"   // will advance to ADULT via age increment

  DEFAULT:
    LOG "Unknown TLB stage"
```

---

### `checkDiapause(TLBEnvironment)`
Determines whether the agent should enter dormancy (diapause) by comparing the current photoperiod (day length at the agent's latitude) against a latitude-specific critical day length (CDL).
In late summer/autumn (week > 25), if the observed day length falls at or below the CDL the agent enters diapause; in early spring (before tamarisk budding), the agent remains dormant regardless of day length.
Returns `true` when the agent is dormant and `false` when it is in the active growing season.
```
READ currentLat = this.tlbLatY

// Photoperiod lookup
COMPUTE currentDayLength = getDayLength(currentLat, state.currentWeek)
  // uses pre-computed latitude × week lookup table

// Latitude-specific critical day length threshold (linear regression formula)
COMPUTE tlbCDL = 0.0451 * currentLat + 12.904

LOG latitude, currentDayLength, tlbCDL to debugWriter and console

// Autumn / winter diapause (post-week-25)
IF state.currentWeek > 25 AND currentDayLength <= tlbCDL:
  RETURN true   // day length too short — enter dormancy

// Early-season logic (week <= 25)
ELSE:
  IF state.currentWeek >= state.mpTamariskBud:
    RETURN false  // tamarisk has budded — agent is active
  ELSE:
    RETURN true   // tamarisk not yet budded — remain dormant
```

---

### `dispersal(TLBEnvironment)`
Triggers movement when the agent is at a patch with no tamarisk food (pTamarisk == 0) and updates the agent's tamarisk quality reading at its new location.
It delegates the actual coordinate update and bounds checking to `findANewLocation()`, and if that call results in death (outside RESET area) this method returns without further action.
```
// Step 1: relocate
CALL findANewLocation(state)
  // SIDE EFFECTS: updates tlbLonX, tlbLatY, vegGridX, vegGridY,
  //               displayX, displayY, patchID, terrID
  // MAY CALL death() if new location is outside RESET boundary

// Step 2: refresh tamarisk quality at the new patch
READ currentPTamarisk = state.tamariskInfo[this.patchID].pTamarisk

LOG patchID and new currentPTamarisk to debugWriter and console
```

---

### `findANewLocation(TLBEnvironment)`
Samples a random dispersal distance from an exponential distribution (parameterised by `mpTlbDisperse`) and a uniformly random direction, then computes the new geographic coordinates using trigonometry.
Both the vegetation-grid and display-grid coordinates are recalculated, the agent is placed on the display grid, and the patch and territory IDs are read from the rasters at the new cell.
If the new territory ID is 0 or -1 the agent has moved outside the RESET study area and `death()` is called immediately.
```
// Distance and direction sampling
CREATE ExponentialDistribution with mean = state.mpTlbDisperse
SAMPLE tlbDispDist from distribution   // km or model distance units

SAMPLE tlbDispDir uniformly from [0, 2π]   // radians

// Update geographic coordinates
SET tlbLonX = tlbLonX + tlbDispDist * cos(tlbDispDir)
SET tlbLatY = tlbLatY + tlbDispDist * sin(tlbDispDir)

// Convert to vegetation raster grid
COMPUTE vegGridX = longitudeXtoGridX(tlbLonX, xllcornerVeg, vegCellSize)
COMPUTE vegGridY = latitudeYtoGridY(tlbLatY,  yllcornerVeg, vegCellSize, nRowsVeg)

// Convert to display grid
COMPUTE displayX = getVegToDisplayX(state, vegGridX)
COMPUTE displayY = getVegToDisplayY(state, vegGridY)

// Update agent position in MASON's spatial grid
CALL state.agentGrid.setObjectLocation(this, displayX, displayY)

// Read spatial identifiers from rasters at new cell
SET patchID = state.getPatchIDByLoc(state, vegGridX, vegGridY)
SET terrID  = state.getTerrIDByLoc(state, vegGridX, vegGridY)

// Boundary check
IF terrID == -1 OR terrID == 0:
  LOG "agent outside RESET area — dies"
  SET actionExecuted = "ADULT: disperseOutsideRESET_death"
  INCREMENT state.numDeath
  CALL death(state)
  RETURN
```

---

### `feed_colonizeACell(TLBEnvironment)`
Registers the agent as an active feeder in the `TLBTerritory` that occupies its current vegetation cell, establishing the link between agent feeding pressure and territory-level defoliation tracking.
If the agent's current territory ID is 0, it is outside the RESET study boundary and is killed.
If the territory object cannot be found at the expected grid cell (coverage gap), a warning is logged but the agent is not killed.
```
IF terrID == 0:   // outside RESET study area
  INCREMENT state.numDeath
  LOG "feedOutsideRESET_death"
  SET actionExecuted = "ADULT: feedOutsideRESET_death"
  CALL death(state)
  RETURN

// Look up territory from spatial grid
GET territory = state.territoryGrid.get(vegGridX, vegGridY)

IF territory != null:
  ADD this agent to territory.memberAgents   // registers feeding pressure
  SET this.tlbHostTerritory = territory      // back-reference for agent
ELSE:
  // Raster coverage gap — log warning, do not kill
  LOG "WARNING: no territory found at vegGridX=<X>, vegGridY=<Y>"
```

---

### `laying(TLBEnvironment, TLBAgent parent)`
Creates a clutch of `tlbSpawn` new egg agents at the parent's geographic coordinates, each with the next generation index and a freshly assigned unique ID.
Every newborn is immediately scheduled in the MASON simulation loop and placed on the display grid, and its birth location and timestamp are recorded in its life-history maps.
The global birth counter `state.numBirth` is incremented by the full clutch size.
```
READ birthLon  = parent.tlbLonX
READ birthLat  = parent.tlbLatY
COMPUTE newGeneration = parent.tlbGen + 1

FOR i = 0 TO parent.tlbSpawn - 1:
  // Assign unique ID
  SET newbornID = state.tlbAgentID
  INCREMENT state.tlbAgentID

  // Create egg agent at parent's location
  CREATE newborn = new TLBAgent(
    state      = state,
    agentID    = newbornID,
    stage      = TLBEGG,
    longitude  = birthLon,
    latitude   = birthLat,
    patchID    = parent.patchID,
    terrID     = parent.terrID,
    age        = 0,
    generation = newGeneration
  )

  // Schedule and place in simulation
  SET newborn.event = state.schedule.scheduleRepeating(newborn)
  CALL state.agentGrid.setObjectLocation(newborn, newborn.displayLocation)

  // Record birth life-history
  SET newborn.actionExecuted = "born!"
  STORE newborn.dateData["birthday"]    = currentStep
  STORE newborn.locationData["lonAtBirth"]   = birthLon
  STORE newborn.locationData["latAtBirth"]   = birthLat
  STORE newborn.locationData["patchAtBirth"] = parent.patchID

  LOG "<parentID> created newborn <newbornID>"

INCREMENT state.numBirth by parent.tlbSpawn
```

---

### `death(TLBEnvironment)`
Collects the agent's final state (time step, coordinates, life stage, and age at death) and writes a complete life-history record to the agent summary output file.
After writing, the agent is permanently removed from the simulation by stopping its scheduled event, so `step()` will never be called again.
```
// Capture death metadata
STORE dateData["dateOfDeath"]    = currentStep
STORE locationData["lonAtDeath"] = this.tlbLonX
STORE locationData["latAtDeath"] = this.tlbLatY
STORE stageData["deathStage"]    = this.tlbStage
STORE ageData["deathAge"]        = this.tlbAge

// Format CSV life-history record
FORMAT lifeHistoryInfo =
  currentStep, tlbAgentID,
  dateData["birthday"],      dateData["dateOfDeath"],
  locationData["lonAtBirth"], locationData["latAtBirth"], locationData["patchAtBirth"],
  locationData["lonAtDeath"], locationData["latAtDeath"],
  stageData["deathStage"],   ageData["deathAge"]

// Persist record
WRITE lifeHistoryInfo to state.agentSummaryWriter

// Remove from MASON scheduler
CALL event.stop()
```

---

### `setTlbHostTerritory(TLBTerritory)`
A simple setter that stores a reference to the territory the agent is currently feeding in.
This reference allows other components to query the agent's host territory directly without a spatial lookup.
```
SET this.tlbHostTerritory = territory
```

---
---

## TLBTerritory

### Constructor — `TLBTerritory(...)`
Initializes a territory patch with its spatial identity, initial vegetation density, and cumulative defoliation history loaded from the input rasters.
Both `memberCells` and `memberAgents` are created as empty MASON `Bag` objects; `memberCells` is populated at startup when the territory raster is parsed, and `memberAgents` is filled dynamically as agents feed.
The initial tamarisk density `pTamariskAtStart` is stored separately so that spring regrowth can always restore the territory to its baseline condition.
```
GIVEN terrID, patchID, terrNumDefoliation, terrTamariskDensity, pTamarisk, permanentlyDefoliated

  // Identity
  SET this.terrID  = terrID
  SET this.patchID = patchID

  // Membership containers
  INITIALIZE memberCells  = new empty Bag   // filled during territory raster parsing
  INITIALIZE memberAgents = new empty Bag   // filled each tick by feed_colonizeACell()

  // TLB impact counters
  SET terrNTlb             = 0
  SET terrTotalTamariskFeed = 0.0

  // Vegetation state
  SET terrNumDefoliation   = terrNumDefoliation   // loaded from prior model state or 0
  SET pTamariskAtStart     = pTamarisk            // baseline density for spring regrowth
  SET terrTamariskDensity  = terrTamariskDensity  // current density (may differ if damage carried over)
  SET permanentlyDefoliated = permanentlyDefoliated
```

---

### `step(SimState state)`
The weekly tick for each territory. It counts the agents currently feeding, computes the feeding-pressure ratio, and determines whether the agent load is high enough to trigger a defoliation event.
At the start of each spring (week == 1) the territory either resets to its baseline tamarisk density (if total defoliation events < 5) or is locked at zero density and flagged as permanently defoliated (if defoliation events >= 5).
At the end of each tick the current territory state is written as a CSV row to the impact output file.
```
CAST state to TLBEnvironment eState

// ── Agent census ─────────────────────────────────────────────
UPDATE terrNTlb             = memberAgents.numObjs
UPDATE terrTotalTamariskFeed = (double) terrNTlb / eState.mpTlbFeed
  // ratio > 1.0 means feeding pressure exceeds carrying capacity

// ── Defoliation threshold check ──────────────────────────────
IF terrNTlb >= eState.mpTlbFeed AND NOT permanentlyDefoliated:
  INCREMENT terrNumDefoliation by 1
  DECREMENT terrTamariskDensity by 1
  // Agent count meets or exceeds the defoliation threshold for this territory

// ── Annual spring reset (runs once per year at week 1) ───────
IF eState.currentWeek == 1:
  IF terrNumDefoliation >= 5:
    // Threshold for irreversible damage has been reached
    SET terrTamariskDensity  = 0
    SET permanentlyDefoliated = true
  ELSE:
    // Tamarisk regrows from dormancy back to its original density
    SET terrTamariskDensity = pTamariskAtStart

// ── Output logging ───────────────────────────────────────────
FORMAT impactInfo =
  eState.currentYear, eState.currentWeek,
  this.terrID, this.patchID,
  this.pTamariskAtStart, this.terrNTlb,
  this.terrTamariskDensity, this.terrNumDefoliation,
  this.permanentlyDefoliated

WRITE impactInfo to eState.impactWriter
```
