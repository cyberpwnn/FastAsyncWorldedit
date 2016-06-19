package com.boydti.fawe.regions.general.plot;

import com.boydti.fawe.FaweCache;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.fawe.object.RunnableVal2;
import com.boydti.fawe.object.clipboard.LazyClipboard;
import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.SetQueue;
import com.boydti.fawe.util.TaskManager;
import com.intellectualcrafters.jnbt.CompoundTag;
import com.intellectualcrafters.jnbt.Tag;
import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.RegionWrapper;
import com.intellectualcrafters.plot.object.RunnableVal;
import com.intellectualcrafters.plot.util.MainUtil;
import com.intellectualcrafters.plot.util.SchematicHandler;
import com.intellectualcrafters.plot.util.block.LocalBlockQueue;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.SchematicWriter;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

public class FaweSchematicHandler extends SchematicHandler {
    @Override
    public boolean restoreTile(LocalBlockQueue queue, CompoundTag compoundTag, int x, int y, int z) {
        if (queue instanceof FaweLocalBlockQueue) {
            queue.setTile(x, y, z, compoundTag);
            return true;
        }
        FaweQueue faweQueue = SetQueue.IMP.getNewQueue(queue.getWorld(), true, false);
        faweQueue.setTile(x, y, z, (com.sk89q.jnbt.CompoundTag) FaweCache.asTag(compoundTag));
        faweQueue.flush();
        return false;
    }

    @Override
    public void getCompoundTag(final String world, final Set<RegionWrapper> regions, final RunnableVal<CompoundTag> whenDone) {
        TaskManager.IMP.async(new Runnable() {
            @Override
            public void run() {
                Location[] corners = MainUtil.getCorners(world, regions);
                Location pos1 = corners[0];
                Location pos2 = corners[1];
                final CuboidRegion region = new CuboidRegion(new Vector(pos1.getX(), pos1.getY(), pos1.getZ()), new Vector(pos2.getX(), pos2.getY(), pos2.getZ()));
                final EditSession editSession = new EditSessionBuilder(pos1.getWorld()).checkMemory(false).fastmode(true).limitUnlimited().changeSetNull().autoQueue(false).build();

                final int mx = pos1.getX();
                final int my = pos1.getY();
                final int mz = pos1.getZ();

                LazyClipboard clipboard = new LazyClipboard(region) {
                    @Override
                    public BaseBlock getBlock(int x, int y, int z) {
                        return editSession.getLazyBlock(mx + x, my + y, mz + z);
                    }

                    public BaseBlock getBlockAbs(int x, int y, int z) {
                        return editSession.getLazyBlock(x, y, z);
                    }

                    @Override
                    public List<? extends Entity> getEntities() {
                        return editSession.getEntities(region);
                    }

                    @Override
                    public void forEach(RunnableVal2<Vector, BaseBlock> task, boolean air) {
                        Vector mutable = new Vector(0, 0, 0);
                        for (RegionWrapper region : regions) {
                            for (int z = region.minZ; z <= region.maxZ; z++) {
                                mutable.z = z - region.minZ;
                                for (int y = region.minY; y <= Math.min(255, region.maxY); y++) {
                                    mutable.y = y - region.minY;
                                    for (int x = region.minX; x <= region.maxX; x++) {
                                        mutable.x = x - region.minX;
                                        BaseBlock block = editSession.getLazyBlock(x, y, z);
                                        if (!air && block == editSession.nullBlock) {
                                            continue;
                                        }
                                        task.run(mutable, block);
                                    }
                                }
                            }
                        }
                    }
                };

                Clipboard holder = new BlockArrayClipboard(region, clipboard);
                com.sk89q.jnbt.CompoundTag weTag = SchematicWriter.writeTag(holder);
                CompoundTag tag = new CompoundTag((Map<String, Tag>) (Map<?,?>) weTag.getValue());
                whenDone.run(tag);
            }
        });
    }

    @Override
    public boolean save(CompoundTag tag, String path) {
        if (tag == null) {
            PS.debug("&cCannot save empty tag");
            return false;
        }
        try {
            File tmp = MainUtil.getFile(PS.get().IMP.getDirectory(), path);
            tmp.getParentFile().mkdirs();
            com.sk89q.jnbt.CompoundTag weTag = (com.sk89q.jnbt.CompoundTag) FaweCache.asTag(tag);
            try (OutputStream stream = new FileOutputStream(tmp); NBTOutputStream output = new NBTOutputStream(new GZIPOutputStream(stream))) {
                Map<String, com.sk89q.jnbt.Tag> map = weTag.getValue();
                output.writeNamedTag("Schematic", map.containsKey("Schematic") ? map.get("Schematic") : weTag);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void upload(final CompoundTag tag, final UUID uuid, final String file, final RunnableVal<URL> whenDone) {
        if (tag == null) {
            PS.debug("&cCannot save empty tag");
            com.intellectualcrafters.plot.util.TaskManager.runTask(whenDone);
            return;
        }
        MainUtil.upload(uuid, file, "schematic", new RunnableVal<OutputStream>() {
            @Override
            public void run(OutputStream output) {
                try {
                    GZIPOutputStream gzip = new GZIPOutputStream(output, true);
                    com.sk89q.jnbt.CompoundTag weTag = (com.sk89q.jnbt.CompoundTag) FaweCache.asTag(tag);
                    NBTOutputStream nos = new NBTOutputStream(gzip);
                    Map<String, com.sk89q.jnbt.Tag> map = weTag.getValue();
                    nos.writeNamedTag("Schematic", map.containsKey("Schematic") ? map.get("Schematic") : weTag);
                    gzip.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, whenDone);
    }
}
