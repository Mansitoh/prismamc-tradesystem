package com.prismamc.trade.commands.base;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Create a minecraft command without plugin.yml
 * https://gist.github.com/redsarow/46a9eb30991bf6007508f72aba7da89f
 * 
 * @author redsarow
 */
public abstract class AMyCommand<T extends JavaPlugin> extends Command implements CommandExecutor, PluginIdentifiableCommand {

    private static CommandMap commandMap;

    static {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            commandMap = (CommandMap) f.get(Bukkit.getServer());
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private final T plugin;
    private final HashMap<Integer, ArrayList<TabCommand>> tabComplete;
    private boolean register = false;

    /**
     * @param plugin plugin responsible of the command.
     * @param name   name of the command.
     */
    protected AMyCommand(T plugin, String name) {
        super(name);

        assert commandMap != null;
        assert plugin != null;
        assert name.length() > 0;

        setLabel(name);
        this.plugin = plugin;
        tabComplete = new HashMap<>();
    }

    protected void setAliases(String... aliases) {
        if (aliases != null && (register || aliases.length > 0))
            setAliases(Arrays.stream(aliases).collect(Collectors.toList()));
    }

    protected void addTabbComplete(int indice, String permission, String[] beforeText, String... arg) {
        if (arg != null && arg.length > 0 && indice >= 0) {
            if (tabComplete.containsKey(indice)) {
                tabComplete.get(indice).addAll(Arrays.stream(arg).collect(
                        ArrayList::new,
                        (tabCommands, s) -> tabCommands.add(new TabCommand(indice, s, permission, beforeText)),
                        ArrayList::addAll));
            } else {
                tabComplete.put(indice, Arrays.stream(arg).collect(
                        ArrayList::new,
                        (tabCommands, s) -> tabCommands.add(new TabCommand(indice, s, permission, beforeText)),
                        ArrayList::addAll)
                );
            }
        }
    }

    protected void addTabbComplete(int indice, String... arg) {
        addTabbComplete(indice, null, null, arg);
    }

    protected boolean registerCommand() {
        if (!register) {
            register = commandMap.register(plugin.getName(), this);
        }
        return register;
    }

    @Override
    public T getPlugin() {
        return this.plugin;
    }

    public HashMap<Integer, ArrayList<TabCommand>> getTabComplete() {
        return tabComplete;
    }

    @Override
    public boolean execute(CommandSender commandSender, String command, String[] arg) {
        if (getPermission() != null) {
            if (!commandSender.hasPermission(getPermission())) {
                if (getPermissionMessage() == null) {
                    commandSender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando!");
                } else {
                    commandSender.sendMessage(getPermissionMessage());
                }
                return false;
            }
        }
        if (onCommand(commandSender, this, command, arg))
            return true;
        commandSender.sendMessage(ChatColor.RED + getUsage());
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        int indice = args.length - 1;

        if ((getPermission() != null && !sender.hasPermission(getPermission())) || tabComplete.size() == 0 || !tabComplete.containsKey(indice))
            return super.tabComplete(sender, alias, args);

        List<String> list = tabComplete.get(indice).stream()
                .filter(tabCommand -> tabCommand.getTextAvant() == null || tabCommand.getTextAvant().contains(args[indice - 1]))
                .filter(tabCommand -> tabCommand.getPermission() == null || sender.hasPermission(tabCommand.getPermission()))
                .filter(tabCommand -> tabCommand.getText().startsWith(args[indice]))
                .map(TabCommand::getText)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        return list.size() < 1 ? super.tabComplete(sender, alias, args) : list;
    }

    private static class TabCommand {
        private final int indice;
        private final String text;
        private final String permission;
        private final ArrayList<String> textAvant;

        private TabCommand(int indice, String text, String permission, String... textAvant) {
            this.indice = indice;
            this.text = text;
            this.permission = permission;
            if (textAvant == null || textAvant.length < 1) {
                this.textAvant = null;
            } else {
                this.textAvant = Arrays.stream(textAvant).collect(ArrayList::new,
                        ArrayList::add,
                        ArrayList::addAll);
            }
        }

        public String getText() {
            return text;
        }

        public int getIndice() {
            return indice;
        }

        public String getPermission() {
            return permission;
        }

        public ArrayList<String> getTextAvant() {
            return textAvant;
        }
    }
}