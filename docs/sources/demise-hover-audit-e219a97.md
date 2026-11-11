# Completed Hover consumer audit

Reference: `YoungJibbit95/DemisePrivate` at `e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`.

All **2,367 Java files** under the pinned `src` tree were fetched and searched in memory, totaling **12,459,760 source bytes**. There were **zero read failures**; every returned blob ID matched its pinned tree entry. No second client checkout or complete source copy was materialized. The complete workspace audit manifest records each path/blob/size and all matching lines. Its digest, pinned tree, totals and relevant consumers are retained in [demise-hover-audit-e219a97.json](demise-hover-audit-e219a97.json).

The scan examined `Hover`, Scaffold references and direct `.addons` references. The only setting-name literal `"Hover"` is its registration in `wtf/demise/features/modules/impl/player/Scaffold.java` line 71. That complete file contains no behavioral read of this option, by name or index; its Addons field is private. Other Hover matches belong to GUI mouse state or Minecraft chat hover events.

All functional consumers of the actual Scaffold module were inspected:

| Consumer | Verified use |
| --- | --- |
| `net/minecraft/client/entity/EntityPlayerSP.java` | Checks Scaffold enabled and Sprint mode=Silent for sprint transitions. This is the only Minecraft/library Java file referring to Scaffold. |
| `wtf/demise/features/modules/impl/movement/Sprint.java` | Skips its automatic sprint-key request while Scaffold is enabled. |
| `wtf/demise/features/modules/impl/movement/TargetStrafe.java` | Disables target-strafe activity while Scaffold is enabled. |
| `wtf/demise/features/modules/impl/movement/Speed.java` | Uses Scaffold enabled state for Speed-module interaction; no Addons access. |
| `wtf/demise/features/modules/impl/player/FastPlace.java` | Skips its independent placement path while Scaffold is enabled. |
| `wtf/demise/features/modules/impl/legit/AutoRod.java` | Avoids resetting its item state while Scaffold is enabled. |
| `wtf/demise/features/modules/impl/exploit/Disabler.java` | Checks only Scaffold enabled in an unrelated packet-mode condition; no Addons access. |
| `wtf/demise/utils/player/rotation/RotationHandler.java` | Exempts active Scaffold from its non-Silent movement-correction sprint cancellation. |
| `wtf/demise/utils/player/rotation/RotationUtils.java` | Imports Scaffold without using it. |

Other Scaffold matches are module registration, the separate LegitScaffold module, diagnostics/check names and a text message. None consumes Scaffold Addons.

**Finding:** Hover is registered and participates in generic settings/configuration UI, but has no behavioral consumer in the pinned Java source. Leaving it inert preserves this reference. Adding a hovering movement algorithm would introduce behavior absent from this source.

The earlier GitHub search-index response was incomplete and was not used as absence evidence. This direct-source audit supersedes the earlier bounded-file search.
