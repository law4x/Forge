/*
 * Minecraft Forge
 * Copyright (c) 2016.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.common.discovery;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.common.ReflectionAPI;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.FileListHelper;

import org.apache.logging.log4j.Level;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;

public class ModDiscoverer
{
    private static Pattern zipJar = Pattern.compile("(.+).(zip|jar)$");

    private List<ModCandidate> candidates = Lists.newArrayList();

    private ASMDataTable dataTable = new ASMDataTable();

    private List<File> nonModLibs = Lists.newArrayList();

    public void findClasspathMods(ModClassLoader modClassLoader)
    {
        List<String> knownLibraries = ImmutableList.<String>builder()
                // skip default libs
                .addAll(modClassLoader.getDefaultLibraries())
                // skip loaded coremods
                .addAll(CoreModManager.getIgnoredMods())
                // skip reparse coremods here
                .addAll(CoreModManager.getReparseableCoremods())
                .build();
        File[] minecraftSources = modClassLoader.getParentSources();
        if (minecraftSources.length == 1 && minecraftSources[0].isFile())
        {
            FMLLog.fine("Minecraft is a file at %s, loading", minecraftSources[0].getAbsolutePath());
            addCandidate(new ModCandidate(minecraftSources[0], minecraftSources[0], ContainerType.JAR, true, true));
        }
        else
        {
            int i = 0;
            for (File source : minecraftSources)
            {
                if (source.isFile())
                {
                    if (knownLibraries.contains(source.getName()) || modClassLoader.isDefaultLibrary(source))
                    {
                        FMLLog.finer("Skipping known library file %s", source.getAbsolutePath());
                    }
                    else if (false == ReflectionAPI.checkPermission(getClass().getClassLoader(),  source)) {//add by yfg。检查官方mod的md5.
                    	FMLLog.finer("File not permitted.Skipping file %s", source.getAbsolutePath());
					}
                    else
                    {
                        FMLLog.fine("Found a minecraft related file at %s, examining for mod candidates", source.getAbsolutePath());
                        addCandidate(new ModCandidate(source, source, ContainerType.JAR, i==0, true));
                    }
                }
                else if (minecraftSources[i].isDirectory())
                {
                	//revise by yfg.不允许mod以目录结构存在。必须以jar包形式存在。
                	/*
                    FMLLog.fine("Found a minecraft related directory at %s, examining for mod candidates", source.getAbsolutePath());
                    addCandidate(new ModCandidate(source, source, ContainerType.DIR, i==0, true));
                    */
                }
                i++;
            }
        }

    }

    public void findModDirMods(File modsDir)
    {
        findModDirMods(modsDir, new File[0]);
    }
    //add by yfg.检查mod权限。
    public void checkModDirMods(File modsDir, File[] supplementalModFileCandidates)
    {
    	ClassLoader loader = getClass().getClassLoader();
        File[] modDirList = FileListHelper.sortFileList(modsDir, null);
        List<File> listModList = new LinkedList<File>();
        for (File modFile : modDirList)
        {
            if (modFile.isDirectory())
            {
                FMLLog.fine("Skipping a directory: %s.", modFile.getName());
                continue;
            }
        	if (false == ReflectionAPI.checkPermission(loader, modFile))
        	{
        		FMLLog.fine("Dont have permission.mod:%s", modFile.getName());
        		continue;
        	}
        	listModList.add(modFile);
        }
        File[] modList = (File[]) listModList.toArray(new File[0]);
        modList = FileListHelper.sortFileList(ObjectArrays.concat(modList, supplementalModFileCandidates, File.class));  
        for (File modFile : modList)
        {
            // skip loaded coremods
            if (CoreModManager.getIgnoredMods().contains(modFile.getName()))
            {
                FMLLog.finer("Skipping already parsed coremod or tweaker %s", modFile.getName());
            }
            else if (modFile.isDirectory())
            {
                FMLLog.fine("Found a candidate mod directory %s", modFile.getName());
                addCandidate(new ModCandidate(modFile, modFile, ContainerType.DIR));
            }
            else
            {
                Matcher matcher = zipJar.matcher(modFile.getName());

                if (matcher.matches())
                {
                    FMLLog.fine("Found a candidate zip or jar file %s", matcher.group(0));
                    addCandidate(new ModCandidate(modFile, modFile, ContainerType.JAR));
                }
                else
                {
                    FMLLog.fine("Ignoring unknown file %s in mods directory", modFile.getName());
                }
            }
        }
    }

    public void findModDirMods(File modsDir, File[] supplementalModFileCandidates)
    {
        File[] modList = FileListHelper.sortFileList(modsDir, null);
        modList = FileListHelper.sortFileList(ObjectArrays.concat(modList, supplementalModFileCandidates, File.class));
        for (File modFile : modList)
        {
            // skip loaded coremods
            if (CoreModManager.getIgnoredMods().contains(modFile.getName()))
            {
                FMLLog.finer("Skipping already parsed coremod or tweaker %s", modFile.getName());
            }
            else if (modFile.isDirectory())
            {
                FMLLog.fine("Found a candidate mod directory %s", modFile.getName());
                addCandidate(new ModCandidate(modFile, modFile, ContainerType.DIR));
            }
            else
            {
                Matcher matcher = zipJar.matcher(modFile.getName());

                if (matcher.matches())
                {
                    FMLLog.fine("Found a candidate zip or jar file %s", matcher.group(0));
                    addCandidate(new ModCandidate(modFile, modFile, ContainerType.JAR));
                }
                else
                {
                    FMLLog.fine("Ignoring unknown file %s in mods directory", modFile.getName());
                }
            }
        }
    }

    public List<ModContainer> identifyMods()
    {
        List<ModContainer> modList = Lists.newArrayList();

        for (ModCandidate candidate : candidates)
        {
            try
            {
                List<ModContainer> mods = candidate.explore(dataTable);
                if (mods.isEmpty() && !candidate.isClasspath())
                {
                    nonModLibs.add(candidate.getModContainer());
                }
                else
                {
                    modList.addAll(mods);
                }
            }
            catch (LoaderException le)
            {
                FMLLog.log(Level.WARN, le, "Identified a problem with the mod candidate %s, ignoring this source", candidate.getModContainer());
            }
            catch (Throwable t)
            {
                Throwables.propagate(t);
            }
        }

        return modList;
    }

    public ASMDataTable getASMTable()
    {
        return dataTable;
    }

    public List<File> getNonModLibs()
    {
        return nonModLibs;
    }

    private void addCandidate(ModCandidate candidate)
    {
        for (ModCandidate c : candidates)
        {
            if (c.getModContainer().equals(candidate.getModContainer()))
            {
                FMLLog.finer("  Skipping already in list %s", candidate.getModContainer());
                return;
            }
        }
        candidates.add(candidate);
    }
}
