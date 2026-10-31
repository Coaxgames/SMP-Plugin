# Building and using the Galaxy SMP resource pack

The Blockbench `.bbmodel` files in this folder embed their PNG textures. Gradle extracts those textures and creates a separate Minecraft 26.3 resource-pack ZIP, including flipbook animation metadata.

## Build and test locally

From the repository root, run:

```powershell
.\gradlew.bat resourcePack resourcePackSha1
```

The build produces:

- `build/distributions/galaxy-smp-resource-pack-<version>.zip`
- `build/distributions/galaxy-smp-resource-pack-<version>.zip.sha1`

The pack uses Minecraft 26.3's item-definition/model format and registers its custom item textures in the blocks atlas. To test it locally, copy the ZIP to the client's `.minecraft/resourcepacks` folder and enable it. Astral Sword, Void Sword, and Collapsing Star Mace items created by the plugin have matching `galaxy:<item_id>` item models. Existing items and vanilla items are not globally overridden.


## Configure server delivery

After a release, download the ZIP and its matching `.zip.sha1` sidecar from the GitHub Release. Use the ZIP's direct HTTPS download URL and the sidecar's plain 40-character hexadecimal digest in `server.properties`:

```properties
resource-pack=https://github.com/Coaxgames/SMP-Plugin/releases/download/<version>/galaxy-smp-resource-pack-<version>.zip
resource-pack-sha1=<sha1-from-the-sidecar>
```

Restart the server. Minecraft offers the pack to players, who must accept it to see the custom models. The release asset is not automatically hosted or configured on a running Minecraft server.

## Blockbench flipbook animations

Standalone PNGs and matching `.png.mcmeta` files are copied into the resource pack and registered in the blocks atlas. Parent-based item models (such as `minecraft:item/generated`) can reference standalone textures; `ability_spark.json` uses this for the Collapsing Star Mace's animated ability particle.

Every `.bbmodel` under `Textures/` is discovered automatically; there is no hard-coded model allowlist. Automatic conversion of `.bbmodel` geometry to model JSON is paused while we check whether conversion is responsible for the rendering issue. The pack still contains all extracted embedded PNGs, loose PNGs, and animation metadata. If a matching Blockbench-exported Java item `.json` is present, the build uses it; without one, textures are packaged but no model JSON or item definition is generated for that model.

Blockbench's Java item `.json` export is the model geometry file. `.mcmeta` is not a model export; it is the separate Minecraft texture-animation metadata file. The build writes `.mcmeta` for tall flipbook sheets using the Blockbench frame time and registers item textures in `assets/minecraft/atlases/blocks.json`, which Minecraft requires to render them on item models. Craft recipes that use player heads require heads carrying Head Drop's `headdrop:dropped` marker; the crafting preview explains when that requirement is not met. Python 3 is required to build the pack; use `-PpythonExecutable=<path>` if the interpreter is not available as `python` on Windows or `python3` elsewhere.

`wormhole_rift.json` and `wormhole_rift.bbmodel` provide the Wormhole Trident's animated in-world rift. The exported geometry is rendered on an item display while the rift is active, and the two embedded vertical flipbook textures supply its animation.
