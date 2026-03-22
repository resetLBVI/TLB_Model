# PSEUDOCODE — TLBAgent & TLBTerritory

---

## TLBAgent

### Constructor — `TLBAgent(...)`
Initializes a new TLB agent with location, stage, and life history tracking.
```
GIVEN agentID, stage, longitude, latitude, patchID, terrID, age, generation
  SET identity fields (id, stage, age, generation)
  CONVERT (longitude, latitude) → (vegGridX, vegGridY) using raster grid parameters
  CONVERT (vegGridX, vegGridY)  → (displayX, displayY) for UI
  SET tamarisk quality (currentPTamarisk) from tamariskInfo lookup by patchID
  SET random spawn count within [0, mpTlbSpawn]
  INITIALIZE empty maps: dateData, locationData, stageData, ageData
```

---

### `step(SimState state)`
Main weekly tick. Determines life stage by age, executes stage actions, logs output, increments age.
```
GET currentStep from schedule

DETERMINE stage by age:
  age == 0        → TLBEGG
  age 1–3         → TLBLARVA
  age == 4        → TLBPUPA
  age 5–7         → TLBADULT
  age >= 8        → death(); RETURN

IF week == 0: RESET tlbGen to 0

CALL takeAction(stage)
LOG step data to logWriter
INCREMENT tlbAge by 1
```

---

### `takeAction(Stage, TLBEnvironment)`
Dispatches stage-specific behavior. Each stage applies mortality checks before executing actions.
```
SWITCH on stage:

  TLBADULT:
    IF checkDiapause():
      WITH prob 0.1 → death(); RETURN
      OTHERWISE    → do nothing; RETURN
    ELSE:
      WITH prob mpTlbMort → death(); RETURN
      IF currentPTamarisk == 0 → dispersal()
      IF currentPTamarisk == 1 → feed_colonizeACell()
      IF currentPTamarisk == 2 → feed_colonizeACell(); laying()

  TLBEGG:
    mortality = mpTlbMort + mpAntPredation * 27.3
    IF diapause OR random(mortality) → death(); RETURN

  TLBLARVA:
    mortality = mpTlbMort + mpAntPredation * 35
    IF diapause OR random(mortality) → death(); RETURN
    IF currentPTamarisk == 0        → death(); RETURN
    ELSE                             → feed_colonizeACell()

  TLBPUPA:
    mortality = mpTlbMort + mpAntPredation * 18.5
    IF diapause OR random(mortality) → death(); RETURN
```

---

### `checkDiapause(TLBEnvironment)`
Returns true if the agent should enter dormancy based on day length and season.
```
GET currentDayLength from lookup table (latitude, currentWeek)
COMPUTE criticalDayLength (tlbCDL) = 0.0451 * latitude + 12.904

IF week > 25 AND currentDayLength <= tlbCDL → RETURN true   (autumn/winter diapause)
ELSE IF week >= mpTamariskBud               → RETURN false  (active season)
ELSE                                         → RETURN true   (pre-bud dormancy)
```

---

### `dispersal(TLBEnvironment)`
Moves the agent to a new location and reads the tamarisk quality there.
```
CALL findANewLocation()   // updates vegGridX, vegGridY, patchID, terrID
READ currentPTamarisk from tamariskInfo using new patchID
```

---

### `findANewLocation(TLBEnvironment)`
Samples a random dispersal distance and direction, converts to grid coordinates, and checks bounds.
```
SAMPLE dispersal distance from ExponentialDistribution(mpTlbDisperse)
SAMPLE dispersal direction uniformly from [0, 2π]

UPDATE longitude = longitude + distance * cos(direction)
UPDATE latitude  = latitude  + distance * sin(direction)

CONVERT (longitude, latitude) → (vegGridX, vegGridY)
CONVERT (vegGridX, vegGridY)  → (displayX, displayY)
UPDATE agent location on agentGrid

READ patchID from patch raster at (vegGridX, vegGridY)
READ terrID  from territory raster at (vegGridX, vegGridY)

IF terrID <= 0 → agent is outside RESET area → death(); RETURN
```

---

### `feed_colonizeACell(TLBEnvironment)`
Registers the agent in the territory it currently occupies.
```
IF terrID == 0 → agent is outside RESET area → death(); RETURN

GET territory = territoryGrid.get(vegGridX, vegGridY)
IF territory != null:
  ADD this agent to territory.memberAgents
  SET tlbHostTerritory = territory
ELSE:
  LOG warning: no territory found at current cell
```

---

### `laying(TLBEnvironment, TLBAgent parent)`
Creates a clutch of egg agents at the parent's location and schedules them.
```
FOR i = 0 to parent.tlbSpawn:
  CREATE new TLBAgent at parent's (longitude, latitude)
    with stage = TLBEGG, age = 0, terrID = parent.terrID
  SCHEDULE new agent
  PLACE new agent on agentGrid
  RECORD birth location and birthday in agent's data maps
INCREMENT state.numBirth by tlbSpawn
```

---

### `death(TLBEnvironment)`
Records life history data, writes to output file, and removes agent from schedule.
```
RECORD dateOfDeath, lonAtDeath, latAtDeath, deathStage, deathAge
FORMAT life history string
WRITE to agentSummaryWriter
STOP scheduled event
```

---

### `setTlbHostTerritory(TLBTerritory)`
Sets the agent's current host territory reference.
```
SET tlbHostTerritory = territory
```

---
---

## TLBTerritory

### Constructor — `TLBTerritory(...)`
Initializes a territory with its identity, vegetation state, and empty member bags.
```
GIVEN terrID, patchID, terrNumDefoliation, terrTamariskDensity, pTamarisk, permanentlyDefoliated
  SET identity (terrID, patchID)
  INITIALIZE memberCells  = empty Bag  // pixel coordinates (Int2D) assigned at startup
  INITIALIZE memberAgents = empty Bag  // TLBAgents currently feeding here
  SET terrNTlb = 0, terrTotalTamariskFeed = 0.0
  SET terrNumDefoliation, pTamariskAtStart, terrTamariskDensity, permanentlyDefoliated
```

---

### `step(SimState state)`
Weekly tick for a territory. Updates feeding pressure, applies defoliation, handles spring regrowth, and logs impact.
```
UPDATE terrNTlb = memberAgents.numObjs
UPDATE terrTotalTamariskFeed = terrNTlb / mpTlbFeed

IF terrNTlb >= mpTlbFeed AND NOT permanentlyDefoliated:
  INCREMENT terrNumDefoliation
  DECREMENT terrTamariskDensity by 1

IF currentWeek == 1:  // start of spring
  IF terrNumDefoliation >= 5:
    SET terrTamariskDensity = 0
    SET permanentlyDefoliated = true
  ELSE:
    RESET terrTamariskDensity = pTamariskAtStart  // spring regrowth

LOG impact record to impactWriter
  (year, week, terrID, patchID, pTamariskAtStart, terrNTlb, terrTamariskDensity,
   terrNumDefoliation, permanentlyDefoliated)
```
