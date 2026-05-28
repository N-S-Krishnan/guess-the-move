# Feature 7 — Implementation Plan
## Multi-Variation Navigation & Keyboard Control

---

## Overview of gaps to close

| Area | Gap |
|---|---|
| `Annotation.parentId` | Field exists in the entity but `addAnalysisMove` never sets it |
| `LoadSessionResponse` | Missing `variationTree` field |
| `SessionService.loadSession` | Loads annotations but never builds a tree |
| Store navigation actions | `navigateForward/Backward/SiblingUp/Down/ToEnd/ToStart` don't exist |
| Store `addAnalysisMove` | No deduplication check before calling server |
| Store `loadSession` | Hard-resets `variationTree = []` instead of mapping from response |
| `useVariationKeyNav.ts` | Doesn't exist |
| `VariationTree.vue` | Doesn't exist |
| `AnalysisPanel.vue` | Uses a flat `.variation-line` div, not the tree component |
| `StudyView.vue` | Doesn't mount the keyboard composable |

---

## Phase A — Backend (`chessmind-api`)

### A1. New DTO: `AnnotationTreeNode.kt`

Create `api/api/src/main/kotlin/com/chessmind/api/dto/AnnotationTreeNode.kt`:

```kotlin
data class AnnotationTreeNode(
    val id: String,
    val san: String,
    val uci: String,
    val fen: String,
    val fromFen: String?,
    val symbol: String?,
    val comment: String?,
    val children: List<AnnotationTreeNode>,
)
```

### A2. Update `LoadSessionResponse.kt`

Add `variationTree: List<AnnotationTreeNode> = emptyList()` field. All existing construction
sites that don't pass it will use the default.

### A3. Add repository query to `AnnotationRepository.kt`

```kotlin
fun findFirstBySessionIdAndFenAndMoveUciIsNotNullOrderByCreatedAtAsc(sessionId: UUID, fen: String): Annotation?
```

Used by `addAnalysisMove` to look up whether `fromFen` has an existing annotation row so
`parentId` can be set correctly.

### A4. Fix `addAnalysisMove` in `SessionService.kt`

After the validation call passes, before `annotationRepository.save(...)`, look up:

```kotlin
val parentAnnotation = annotationRepository
    .findFirstBySessionIdAndFenAndMoveUciIsNotNullOrderByCreatedAtAsc(sessionId, request.fromFen)
```

Pass `parentId = parentAnnotation?.id` to the `Annotation(...)` constructor. If `fromFen` is a
mainline FEN (no annotation row exists for it), `parentAnnotation` is `null` and `parentId` is
`null` — the correct root behaviour.

### A5. Update `loadSession` in `SessionService.kt`

After the Redis/rehydration block, before returning `LoadSessionResponse`, build the variation
tree:

```kotlin
val annotations = annotationRepository.findAllBySessionId(sessionId)
val variationTree = buildAnnotationTree(annotations)
```

Private helper `buildAnnotationTree(annotations: List<Annotation>): List<AnnotationTreeNode>`:

1. Filter to move-only rows (`moveUci != null`)
2. Build a `Map<UUID?, List<Annotation>>` grouped by `parentId`
3. Recursively build from the `null` bucket (roots), attaching children ordered by `createdAt`
4. Map each `Annotation` to `AnnotationTreeNode`

Return `variationTree` in `LoadSessionResponse`. The `pending_setup` early-return path keeps
`variationTree = emptyList()`.

### A6. New unit tests in `SessionServiceTest.kt`

Four new tests for the tree-building logic in `loadSession`:

- **Empty** → `variationTree` is `[]`
- **Single linear line** → one root with a chain of single children
- **Forking tree** → one root, two children (two sibling branches)
- **Mixed nodes** → comment-only rows (`moveUci = null`) are excluded from the tree output

---

## Phase B — Frontend (`chessmind-ui`)

### B1. Update `types/session.ts`

Add `variationTree?: VariationNode[]` to `LoadSessionResponse`. `VariationNode` is structurally
identical to `AnnotationTreeNode` from the backend, so it can be reused directly.

### B2. Add six navigation actions to `stores/session.ts`

All are pure synchronous functions — no server calls.

```typescript
function navigateForward(): void
```
- If `currentPath` is empty and `variationTree` is non-empty, push `variationTree[0]` (navigate
  into the first root)
- Otherwise push `currentPath.tip.children[0]` — no-op if no children

```typescript
function navigateBackward(): void
```
- Pop the last node from `currentPath` — no-op if already empty

```typescript
function navigateSiblingDown(): void
```
- Find the tip's siblings: if `currentPath.length === 1` use `variationTree`, else use
  `currentPath.at(-2).children`
- Find tip's index in that list; advance to `(index + 1) % siblings.length`; replace the last
  element of `currentPath`

```typescript
function navigateSiblingUp(): void
```
- Same as `navigateSiblingDown` but `(index - 1 + length) % length`

```typescript
function navigateToEnd(): void
```
- Starting from the current tip (or from `variationTree[0]` if path is empty), follow
  `children[0]` until a leaf; append the whole chain to `currentPath`

```typescript
function navigateToStart(): void
```
- `currentPath.value = []` — returns to the mainline root

All six are added to the store's `return` object so components and tests can reach them.

### B3. Fix `addAnalysisMove` deduplication

Before making the server call, check:

```typescript
const tip = currentPath.value.at(-1)
const siblings = tip ? tip.children : variationTree.value
const existing = siblings.find(n => n.uci === uci)
if (existing) {
  currentPath.value.push(existing)
  return  // no server round-trip, no duplicate annotation row
}
```

### B4. Fix `loadSession` tree mapping

Replace `variationTree.value = []` with:

```typescript
variationTree.value = response.variationTree ?? []
```

Since the backend sends a nested `VariationNode[]`, no recursive helper is needed — the shape
already matches.

Also replace the FEN-based `findPath` helper with an ID-based version:

```typescript
function findPathById(nodes: VariationNode[], targetId: string): VariationNode[] | null {
  for (const node of nodes) {
    if (node.id === targetId) return [node]
    const childPath = findPathById(node.children, targetId)
    if (childPath) return [node, ...childPath]
  }
  return null
}
```

Update `jumpToNode` to use `findPathById(variationTree.value, node.id)`.

### B5. New composable `src/composables/useVariationKeyNav.ts`

- Registers a `keydown` listener on `document` on mount; removes it on unmount
- Guards: `store.mode !== 'analysis'` → return early; `e.target` is `<textarea>` or `<input>` →
  return early
- Key table (Ctrl/Meta + arrow checked **before** bare arrow to avoid shadowing):

| Event | Action |
|---|---|
| `Ctrl/Meta + ArrowRight` | `store.navigateToEnd()` |
| `Ctrl/Meta + ArrowLeft` | `store.navigateToStart()` |
| `ArrowRight` | `store.navigateForward()` |
| `ArrowLeft` | `store.navigateBackward()` |
| `ArrowDown` | `store.navigateSiblingDown()` |
| `ArrowUp` | `store.navigateSiblingUp()` |

- `e.preventDefault()` called for every handled key to suppress page-scroll side effects

Mount in `StudyView.vue` with:

```typescript
import { useVariationKeyNav } from '@/composables/useVariationKeyNav'
useVariationKeyNav()
```

### B6. New component `src/components/VariationTree.vue`

A **recursive** component that takes a single `node: VariationNode` prop.

Template structure:

```html
<span class="vt-node">
  <!-- 1. This node's chip, scrolled into view when active -->
  <button
    ref="chipRef"
    type="button"
    class="variation-move"
    :class="{ 'variation-move--active': isActive }"
    @click="store.jumpToNode(node)"
  >
    {{ node.san }}
  </button>

  <!-- 2. Branch-choice chips: only when this is the active tip AND has 2+ children -->
  <span v-if="isActive && node.children.length > 1" class="branch-choices">
    <button
      v-for="c in node.children"
      :key="c.id"
      type="button"
      class="branch-chip"
      @click.stop="store.jumpToNode(c)"
    >
      → {{ c.san }}
    </button>
  </span>

  <!-- 3. First child continues inline -->
  <VariationTree v-if="mainChild" :node="mainChild" />

  <!-- 4. Side children as indented sub-variations -->
  <div v-for="child in sideChildren" :key="child.id" class="sub-variation">
    <VariationTree :node="child" />
  </div>
</span>
```

Computed properties:

```typescript
const isActive = computed(() => store.currentPath.at(-1)?.id === props.node.id)
const mainChild = computed(() => props.node.children.at(0) ?? null)
const sideChildren = computed(() => props.node.children.slice(1))
```

Scroll behaviour:

```typescript
watch(isActive, (active) => {
  if (active) nextTick(() => chipRef.value?.scrollIntoView({ block: 'nearest' }))
})
```

Self-reference for recursion:

```typescript
import VariationTree from './VariationTree.vue'
```

CSS notes:

- `.vt-node { display: contents }` — makes the wrapper `<span>` transparent to the parent flex
  container so chips flow inline
- `.sub-variation` — `width: 100%` (forces onto new line), `padding-left: 1rem`,
  `border-left: 2px solid #dee2e6`, `margin: 0.25rem 0`, `background: #f8f9fa`,
  `border-radius: 0 4px 4px 0`
- `.branch-choices` — `display: inline-flex; gap: 0.25rem; margin-left: 0.5rem`
- `.branch-chip` — smaller, lighter-weight variant of `.variation-move`

### B7. Update `AnalysisPanel.vue`

- Remove the `.variation-line` div and its entire contents (including the `variation-hint` span
  and the per-node `<button>` loop)
- Remove the `VariationNode` import (no longer needed directly)
- Import `VariationTree` and replace the removed block with:

```html
<div class="variation-tree-container">
  <span v-if="store.variationTree.length === 0" class="variation-hint">
    Make a move to explore a variation
  </span>
  <template v-else>
    <VariationTree
      v-for="root in store.variationTree"
      :key="root.id"
      :node="root"
    />
  </template>
</div>
```

- Keep `MoveSymbolSelector`, `CommentEditor`, `ResumeBanner`, and the take-back button unchanged

### B8. Mount composable in `StudyView.vue`

Add two lines to `<script setup>`:

```typescript
import { useVariationKeyNav } from '@/composables/useVariationKeyNav'
useVariationKeyNav()
```

### B9. New Vitest tests

**`src/__tests__/variationNavigation.spec.ts`** — store navigation actions:

- `navigateForward` on empty tree → no-op
- `navigateForward` from root into first child
- `navigateBackward` from non-empty path → pops correctly
- `navigateBackward` at root → no-op
- `navigateSiblingDown` at a branch point → cycles to next sibling, wraps around
- `navigateSiblingDown` with only one child → no-op
- `navigateSiblingUp` → reverse cycling
- `navigateToEnd` → follows `children[0]` chain to leaf
- `navigateToStart` → clears path
- `addAnalysisMove` deduplication: playing existing child UCI navigates into it without calling
  the service

**`src/__tests__/useVariationKeyNav.spec.ts`** — composable:

- `ArrowRight` dispatches `navigateForward` when `mode === 'analysis'`
- `ArrowLeft` dispatches `navigateBackward` when `mode === 'analysis'`
- `Ctrl+ArrowRight` dispatches `navigateToEnd`
- All six keys are inert when `mode !== 'analysis'`
- Keydown event from inside a `<textarea>` is ignored

---

## Implementation order

```
A1  AnnotationTreeNode DTO
A2  LoadSessionResponse + variationTree field
A3  AnnotationRepository new query method
A4  Fix addAnalysisMove — set parentId on save
A5  loadSession tree building + attach to response
A6  Backend unit tests (tree building)

B1  Update types/session.ts — LoadSessionResponse.variationTree
B2  Add 6 navigation actions + expose in store return
B3  Fix addAnalysisMove deduplication in store
B4  Fix loadSession tree mapping + ID-based jumpToNode
B5  useVariationKeyNav composable
B6  VariationTree.vue recursive component
B7  AnalysisPanel.vue — swap flat div for VariationTree
B8  StudyView.vue — mount composable
B9  Vitest tests
```

---

## Risks and non-obvious decisions

### 1. `parentId` is a retroactive fix
Existing annotation rows in any running database will have `parentId = null`. When `loadSession`
builds the tree, all old annotations become roots with no parent-child relationships. New analysis
made after this feature ships will have correct parentage. A data migration is not required by the
ROADMAP.

### 2. `takeBackAnalysisMove` is not changed
The take-back endpoint uses `findTopBySessionIdOrderByCreatedAtDesc` (most-recently-created
annotation across the whole session). With branching, this can delete the wrong annotation if the
user navigated into an old branch and then took back. Feature 7 does not change the take-back
behaviour — this is a known limitation to address in a future iteration.

### 3. Self-referencing `VariationTree.vue`
Vue handles circular imports for SFCs at build time. The import must be explicit
(`import VariationTree from './VariationTree.vue'`) to work with `<script setup>`. Without it,
the component name won't resolve in the template.

### 4. `display: contents` and scoped styles
Setting `display: contents` on the recursive `<span>` removes it from the box tree. Vue's
`scoped` attribute selector is still applied to the element, but descendant rules that rely on the
element participating in layout may not behave as expected in older browsers. Watch for this
during manual testing; use `:deep()` on the parent if needed.

### 5. Branch-choice chips placement
The branch-choice chips appear immediately after the active node chip and before the sub-variation
blocks. This diverges slightly from Lichess (which renders branch options inline with the move
sequence), but it is simpler to implement and directly matches the ROADMAP wording ("immediately
after the active node").
