/*
 * Simplified Chinese (简体中文)
 *
 * 版权所有 (C) 2023 QiLechan <qilechan@outlook.com> 和本程序的贡献者
 *
 * 本程序是自由软件：你可以再分发之和/或依照由自由软件基金会发布的 GNU 通用公共许可证修改之，无论是版本 3 许可证，还是 3 任何以后版都可以。
 * 发布该程序是希望它能有用，但是并无保障;甚至连可销售和符合某个特定的目的都不保证。请参看 GNU 通用公共许可证，了解详情。
 * 你应该随程序获得一份 GNU 通用公共许可证的副本。如果没有，请看 <https://www.gnu.org/licenses/>。
 * English (英语)
 *
 * Copyright (C) 2023 QiLechan <qilechan@outlook.com> and contributors to this program
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or 3 any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.yuezhikong.newServer.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yuezhikong.newServer.ChatRequest;
import org.yuezhikong.newServer.ServerMain;
import org.yuezhikong.newServer.UserData.user;
import org.yuezhikong.newServer.plugin.Plugin.Plugin;
import org.yuezhikong.newServer.plugin.Plugin.PluginData;
import org.yuezhikong.newServer.plugin.command.CommandExecutor;
import org.yuezhikong.newServer.plugin.event.EventHandler;
import org.yuezhikong.newServer.plugin.event.Listener;
import org.yuezhikong.newServer.plugin.event.events.Event;
import org.yuezhikong.utils.CustomVar;
import org.yuezhikong.utils.SaveStackTrace;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class SimplePluginManager implements PluginManager{
    private final List<Plugin> pluginList = new ArrayList<>();
    private final List<PluginData> pluginDataList = new ArrayList<>();
    @Override
    public void LoadPlugin(@NotNull File PluginFile) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        //启动classloader
        URLClassLoader classLoader = new URLClassLoader(new URL[]{
                PluginFile.toURI().toURL()
        }, ClassLoader.getSystemClassLoader());
        try {
            //读取插件清单
            Properties properties = new Properties();
            properties.load(classLoader.getResourceAsStream("PluginManifest.properties"));//打开插件清单文件
            final String Name = properties.getProperty("Plugin-Name");
            final String Version = properties.getProperty("Plugin-Version");
            final String Author = properties.getProperty("Plugin-Author");
            if (Name == null || Version == null || Author == null)
            {
                ServerMain.getServer().getLogger().error("文件："+PluginFile.getName()+"加载失败，插件清单文件错误");
                return;
            }
            ServerMain.getServer().getLogger().info("正在加载插件 " + Name + " v" + Version + " by " + Author);
            for (Plugin plugin : pluginList)
            {
                PluginData data = plugin.getPluginData();
                if (Name.equals(data.getStaticData().PluginName()))
                {
                    ServerMain.getServer().getLogger().info("无法加载插件 " + Name + " v" + Version + " by " + Author+"因为已安装了同名插件"
                    +data.getStaticData().PluginName()+"v"+data.getStaticData().PluginVersion()+" by"+data.getStaticData().PluginAuthor());
                    return;
                }
            }
            //获取插件主类的Class
            Class<?> jarClass = Class.forName(properties.getProperty("Main-Class"), true, classLoader);
            //检测插件主类是否实现了Plugin接口
            Class<? extends Plugin> pluginClass = jarClass.asSubclass(Plugin.class);
            //调用插件构造器并获取插件
            final Plugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            //写入插件的信息到PluginData
            PluginData.staticData staticData = new PluginData.staticData(plugin, Name, Version, Author, classLoader);
            PluginData pluginData = new PluginData(staticData);
            //设定PluginData
            plugin.setPluginData(pluginData);
            if (plugin.getPluginData() == null) {
                ServerMain.getServer().getLogger().info("无法加载插件 " + Name + " v" + Version + " by " + Author + "因为他的getPluginData方法返回null");
                return;
            }
            //调用插件的onLoad方法
            plugin.onLoad();
            //保存插件到插件列表
            pluginList.add(plugin);
            pluginDataList.add(pluginData);
            ServerMain.getServer().getLogger().info("插件 " + Name + "加载成功");
        }
        catch (Throwable e)
        {
            try {
                classLoader.close();
            } catch (IOException ignored) {}
            if (e instanceof IOException
                    || e instanceof NoSuchMethodException || e instanceof InvocationTargetException
                    || e instanceof InstantiationException || e instanceof IllegalAccessException
                    || e instanceof ClassNotFoundException)
                throw e;
        }
    }

    private record CommandSavedData(String Command,String Description,CommandExecutor executor,Plugin plugin) {}
    private final List<CommandSavedData> commandSavedData = new ArrayList<>();

    @Override
    public boolean RequestPluginCommand(CustomVar.Command CommandInformation, user User) {
        for (CommandSavedData savedData : commandSavedData)
        {
            if (savedData.Command().equals(CommandInformation.Command()))
            {
                savedData.executor().execute(User,CommandInformation.Command(),CommandInformation.argv());
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> getPluginCommandsDescription() {
        List<String> ReturnList = new ArrayList<>();
        for (CommandSavedData savedData : commandSavedData)
        {
            ReturnList.add(savedData.Command()+" "+savedData.Description());
        }
        return ReturnList;
    }

    @Override
    public boolean RegisterCommand(String CommandName, String Description, CommandExecutor executor, Plugin plugin) {
        if (CommandName == null || executor == null || plugin == null)
            return false;
        String command = "/"+CommandName;
        for (CommandSavedData savedData : commandSavedData)
        {
            if (savedData.Command().equals(command))
                return false;
        }
        return commandSavedData.add(new CommandSavedData(command,Description,executor,plugin));
    }

    @Override
    public boolean UnRegisterCommand(String CommandName) {
        if (CommandName == null)
            return false;
        return commandSavedData.removeIf(commandSavedData -> commandSavedData.Command().equals("/"+CommandName));
    }

    @Override
    public void AddEventListener(Listener listener, Plugin plugin) {
        plugin.getPluginData().AddEventListener(listener);
    }

    @Override
    public List<Listener> getEventListener(Plugin plugin) {
        return plugin.getPluginData().getEventListener();
    }

    @Override
    public void RemoveEventListener(Listener listener, Plugin plugin) {
        plugin.getPluginData().RemoveEventListener(listener);
    }

    @Override
    public void UnLoadPlugin(@NotNull Plugin plugin) throws IOException {
        PluginData pluginData = plugin.getPluginData();
        ServerMain.getServer().getLogger().info("正在卸载插件"+pluginData.getStaticData().PluginName()+
                "v"+pluginData.getStaticData().PluginVersion()+
                " by"+pluginData.getStaticData().PluginAuthor());
        for (Listener listener : plugin.getPluginData().getEventListener())
        {
            RemoveEventListener(listener,plugin);
        }
        pluginData.getStaticData().plugin().onUnload();
        pluginList.remove(plugin);
        pluginDataList.remove(pluginData);
        try {
            pluginData.getStaticData().PluginClassLoader().close();
        } catch (IOException e)
        {
            ServerMain.getServer().getLogger().info("无法卸载插件"+pluginData.getStaticData().PluginName()+
                    "v"+pluginData.getStaticData().PluginVersion()+
                    " by"+pluginData.getStaticData().PluginAuthor());
            throw e;
        }
        ServerMain.getServer().getLogger().info("已卸载插件"+pluginData.getStaticData().PluginName()+
                "v"+pluginData.getStaticData().PluginVersion()+
                " by"+pluginData.getStaticData().PluginAuthor());
    }

    @Override
    public void UnLoadAllPlugin() throws IOException {
        record Data(String Name,String Version,String Author,URLClassLoader classLoader){}
        List<Data> DataList = new ArrayList<>();
        for (PluginData pluginData : pluginDataList)
        {
            ServerMain.getServer().getLogger().info("正在卸载插件"+pluginData.getStaticData().PluginName()+
                    "v"+pluginData.getStaticData().PluginVersion()+
                    " by"+pluginData.getStaticData().PluginAuthor());
            for (Listener listener : pluginData.getEventListener())
            {
                RemoveEventListener(listener,pluginData.getStaticData().plugin());
            }
            pluginData.getStaticData().plugin().onUnload();
            DataList.add(new Data(pluginData.getStaticData().PluginName(),
                    pluginData.getStaticData().PluginVersion(),
                    pluginData.getStaticData().PluginAuthor(),
                    pluginData.getStaticData().PluginClassLoader()));
        }
        pluginList.clear();
        pluginDataList.clear();
        for (Data data : DataList)
        {
            try {
                data.classLoader().close();
            } catch (IOException e)
            {
                ServerMain.getServer().getLogger().info("无法卸载插件"+data.Name()+
                        "v"+data.Version()+
                        " by"+data.Author());
                DataList.clear();
                throw e;
            }
            ServerMain.getServer().getLogger().info("已卸载插件"+data.Name()+
                    "v"+data.Version()+
                    " by"+data.Author());
        }
        DataList.clear();
    }

    /**
     * 获取文件夹下所有.jar结尾的文件
     * 一般是插件文件
     * @param Directory 文件夹
     * @return 文件列表
     */
    private @Nullable List<File> GetPluginFileList(@NotNull File Directory)
    {
        if (Directory.isDirectory())
        {
            String[] list = Directory.list();
            if (list == null)
            {
                return null;
            }
            List<File> PluginList = new ArrayList<>();
            for (String s : list) {
                if (s.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    File file1 = new File(Directory.getPath() + "\\" + s);
                    PluginList.add(file1);
                }
            }
            return PluginList;
        }
        else
            return null;
    }

    @Override
    public void LoadPluginOnDirectory(@NotNull File Directory) {
        if (Directory.exists())
        {
            if (Directory.isDirectory())
            {
                List<File> fileList = Objects.requireNonNull(GetPluginFileList(Directory));
                for (File file : fileList)
                {
                    try {
                        LoadPlugin(file);
                    }
                    catch (ClassCastException e)
                    {
                        ServerMain.getServer().getLogger().error("文件："+file.getName()+"加载失败，未实现Plugin接口");
                    }
                    catch (IOException e)
                    {
                        SaveStackTrace.saveStackTrace(e);
                        ServerMain.getServer().getLogger().error("文件："+file.getName()+"加载失败，出现IO错误");
                    }
                    catch (NoSuchMethodException e)
                    {
                        ServerMain.getServer().getLogger().error("文件："+file.getName()+"加载失败，无参构造器不存在");
                    }
                    catch (InvocationTargetException e)
                    {
                        ServerMain.getServer().getLogger().error("文件："+file.getName()+"加载失败，插件构造器出现内部错误");
                        SaveStackTrace.saveStackTrace(e.getCause());
                    }
                    catch (InstantiationException e)
                    {
                        ServerMain.getServer().getLogger().error("文件："+file.getName()+"加载失败，主类是抽象方法或接口");
                    }
                    catch (IllegalAccessException e)
                    {
                        ServerMain.getServer().getLogger().error("文件："+file.getName()+"加载失败，没有权限访问无参构造器");
                    }
                    catch (ClassNotFoundException e)
                    {
                        ServerMain.getServer().getLogger().error("文件："+file.getName()+"加载失败，插件指定的主类不存在");
                    }
                }
            }
            else
            {
                if (!(Directory.delete()))
                {
                    throw new RuntimeException("无法删除文件");
                }
                if (!(Directory.mkdir()))
                {
                    throw new RuntimeException("创建文件夹失败");
                }
            }
        }
        else
        {
            if (!(Directory.mkdir()))
            {
                throw new RuntimeException("创建文件夹失败");
            }
        }
    }

    @Override
    public void callEvent(@NotNull Event event) {
        record MethodData(Method method,Listener listener,PluginData pluginData) {}
        List<MethodData> LowPriorityMethod = new ArrayList<>();
        List<MethodData> NormalPriorityMethod = new ArrayList<>();
        List<MethodData> HighPriorityMethod = new ArrayList<>();
        for (Plugin plugin : pluginList)
        {
            PluginData data = plugin.getPluginData();
            for (Listener listener : data.getEventListener())//获取PluginData中的EventListener后遍历
            {
                Method[] methods = listener.getClass().getDeclaredMethods();//获取方法列表
                for (Method method : methods) {//遍历所有方法
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (
                            parameterTypes.length == 1 //判断此方法要求提供的参数是1个
                                    && parameterTypes[0] == event.getClass() //判断此方法是否要求的类型是否与Event一致
                                    && method.isAnnotationPresent(EventHandler.class) //判断是否被EventHandler注解
                    ) {
                        switch (method.getAnnotation(EventHandler.class).Priority()) {//根据优先级拉入对应的List
                            case LOW -> LowPriorityMethod.add(new MethodData(method,listener,data));
                            case NORMAL -> NormalPriorityMethod.add(new MethodData(method,listener,data));
                            case HIGH -> HighPriorityMethod.add(new MethodData(method,listener,data));
                        }
                    }
                }
            }
        }
        //开始调用方法
        List<MethodData> mergedList = new ArrayList<>();
        mergedList.addAll(LowPriorityMethod);
        mergedList.addAll(NormalPriorityMethod);
        mergedList.addAll(HighPriorityMethod);
        for (MethodData methodData : mergedList)//开始调用
        {
            try {
                methodData.method().invoke(methodData.listener(), event);
            } catch (IllegalAccessException e) {
                ServerMain.getServer().getLogger().error("没有权限访问 插件 "+methodData.pluginData().getStaticData().PluginName()+
                        "v"+methodData.pluginData().getStaticData().PluginVersion()+
                        " by "+methodData.pluginData().getStaticData().PluginAuthor()+
                        "的事件处理程序");
            } catch (InvocationTargetException e) {
                e.getCause().printStackTrace();
                ServerMain.getServer().getLogger().error("插件 "+methodData.pluginData().getStaticData().PluginName()+
                        "v"+methodData.pluginData().getStaticData().PluginVersion()+
                        " by "+methodData.pluginData().getStaticData().PluginAuthor()+
                        "的事件处理程序出现内部错误");
            }
        }
    }

    @Override
    public @Nullable Plugin getPluginByName(@NotNull String name) {
        for (Plugin plugin : pluginList)
        {
            if (name.equals(plugin.getPluginData().getStaticData().PluginName()))
            {
                return plugin;
            }
        }
        return null;
    }

    @Override
    public int getPluginNumber() {
        return pluginList.size();
    }

    @Override
    public List<PluginData> getPluginDataList() {
        return new ArrayList<>(pluginDataList);
    }
}
