# Building and using the Galaxy SMP resource pack

The Blockbench `.bbmodel` files in this folder embed their PNG textures. You do not need to export PNGs manually: the Gradle build extracts them and creates a separate Minecraft 26.3 resource-pack ZIP.

## Build and test locally

From the repository root, run:

```powershell
.\gradlew.bat resourcePack resourcePackSha1
```

The build produces:

- `build/distributions/galaxy-smp-resource-pack-<version>.zip`
- `build/distributions/galaxy-smp-resource-pack-<version>.zip.sha1`

The pack uses Minecraft 26.3's item-definition/model format. To test it locally, copy the ZIP to the client's `.minecraft/resourcepacks` folder and enable it. Astral Sword, Void Sword, and Collapsing Star Mace items created by the plugin have matching `galaxy:<item_id>` item models. Existing items and vanilla items are not globally overridden.

## Configure server delivery

After a release, download the ZIP and its matching `.zip.sha1` sidecar from the GitHub Release. Use the ZIP's direct HTTPS download URL and the sidecar's plain 40-character hexadecimal digest in `server.properties`:

```properties
resource-pack=https://github.com/Coaxgames/SMP-Plugin/releases/download/<version>/galaxy-smp-resource-pack-<version>.zip
resource-pack-sha1=<sha1-from-the-sidecar>
```

Restart the server. Minecraft offers the pack to players, who must accept it to see the custom models. The release asset is not automatically hosted or configured on a running Minecraft server.

## Blockbench flipbook animations

Every `.bbmodel` under `Textures/` is discovered automatically; there is no hard-coded model allowlist. When a matching Blockbench-exported Java item `.json` exists, that JSON is used as the model geometry and its texture links are resolved to the embedded PNGs. If no exported JSON exists, the build converts the `.bbmodel` geometry to a Minecraft item model. It extracts every embedded PNG, including textures not referenced by the exported model, and also copies loose PNGs and their `.mcmeta` files from this folder into the pack.

Blockbench's Java item `.json` export is the model geometry file. `.mcmeta` is not a model export; it is the separate Minecraft texture-animation metadata file. The build writes `.mcmeta` for tall flipbook sheets using the Blockbench frame time. Minecraft plays these texture animations through the resource pack. This is separate from animating model geometry with bones/keyframes, which regular Minecraft item models do not support. Python 3 is required to build the pack; use `-PpythonExecutable=<path>` if the interpreter is not available as `python` on Windows or `python3` elsewhere.
