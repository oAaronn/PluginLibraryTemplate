package me.rainnny.api.command;

import me.rainnny.api.Options;
import me.rainnny.api.protocol.wrapped.WrappedMethod;
import me.rainnny.api.util.MiscUtils;
import me.rainnny.api.util.Style;
import me.rainnny.api.util.TriTuple;
import me.rainnny.api.util.Tuple;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.Bukkit.getPluginManager;

/**
 * @author Braydon
 */
public class CommandHandler implements Listener {
    /**
     * commands: Class instance, with a tuple with provider method, and command annotation
     * arguments: parent command annotation, with a list of tri tuples with the argument class instance, provider method, and argument annotation
     */
    private static final Map<Object, Tuple<Method, Command>> commands = new HashMap<>();
    private static final Map<Command, List<TriTuple<Object, Method, Command>>> arguments = new HashMap<>();

    public CommandHandler(JavaPlugin plugin) {
        getPluginManager().registerEvents(this, plugin);
    }

    /**
     * This handles executing commands from the terminal
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void onCommand(ServerCommandEvent event) {
        if (handleCommandInput(event.getSender(), event.getCommand()))
            event.setCancelled(true);
    }

    /**
     * This handles executing commands as a player
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void onCommand(PlayerCommandPreprocessEvent event) {
        if (handleCommandInput(event.getPlayer(), event.getMessage()))
            event.setCancelled(true);
    }

    private boolean handleCommandInput(CommandSender sender, String input) {
        boolean slash = input.startsWith("/");
        String[] split = slash ? input.substring(1).split(" ") : input.split(" ");
        String label = split[0];
        if (label.isEmpty())
            return false;
        String[] args = new String[split.length - 1];
        System.arraycopy(split, 1, args, 0, split.length - 1);

        for (Tuple<Method, Command> tuple : commands.values()) {
            Command command = tuple.getRight();
            if (!isAlias(command, label))
                continue;
            if (!canExecute(command, sender))
                return true;
            else {
                try {
                    if (args.length > 0) {
                        List<TriTuple<Object, Method, Command>> arguments = CommandHandler.arguments.getOrDefault(command, new ArrayList<>());
                        for (TriTuple<Object, Method, Command> triTuple : arguments) {
                            Command argument = triTuple.getRight();
                            if (!isAlias(argument, args[0]))
                                continue;
                            if (!canExecute(argument, sender))
                                return true;
                            label = args[0];
                            args = new String[split.length - 2];
                            System.arraycopy(split, 2, args, 0, split.length - 2);
                            new WrappedMethod(triTuple.getMiddle()).invoke(triTuple.getLeft(),
                                    new CommandProvider(sender, label, args));
                            return true;
                        }
                    }
                    handleCommand(sender, label, args, command);
                } catch (Exception ex) {
                    if (ex instanceof CommandException)
                        sender.sendMessage("§c" + ex.getMessage());
                    else sender.sendMessage("§cThere was a problem whilst executing the command! §f" + ex.getLocalizedMessage());
                    ex.printStackTrace();
                }
            }
            return true;
        }
        return false;
    }

    private void handleCommand(CommandSender sender, String label, String[] args, Command command) {
        if (canExecute(command, sender)) {
            List<TriTuple<Object, Method, Command>> arguments = CommandHandler.arguments.getOrDefault(command, new ArrayList<>());
            if (command.displayHelp() && arguments.size() >= 1) {
                displayHelp(command, label, sender);
                return;
            }
            Tuple<Object, Method> tuple = null;
            for (Map.Entry<Object, Tuple<Method, Command>> entry : commands.entrySet()) {
                if (entry.getValue().getRight().equals(command)) {
                    tuple = new Tuple<>(entry.getKey(), entry.getValue().getLeft());
                    break;
                }
            }
            if (tuple == null) {
                sender.sendMessage("§cThere was a problem whilst executing the command! §fUnknown class or method");
                return;
            }
            new WrappedMethod(tuple.getRight()).invoke(tuple.getLeft(), new CommandProvider(sender, label, args));
        }
    }

    /**
     * Displays the help menu to the provided sender for the provided command
     * @param command - The command you would like to send help for
     * @param label - The command label, this is used to display /<label> in the help menu
     * @param sender - The sender you would like to display the help menu to
     */
    private void displayHelp(Command command, String label, CommandSender sender) {
        sender.sendMessage(command.colorScheme().primaryColor() + "§l" + WordUtils.capitalize(command.name().toLowerCase()) + " " + command.colorScheme().tertiaryColor() + "Commands:");
        sendChatCommand(command, command, label, sender);
        for (TriTuple<Object, Method, Command> triTuple : arguments.getOrDefault(command, new ArrayList<>())) {
            sendChatCommand(command, triTuple.getRight(), label, sender);
        }
    }

    /**
     * This handles formatting for each command
     */
    private void sendChatCommand(Command parent, Command argument, String label, CommandSender sender) {
        String description = parent.equals(argument) ? "Show's this menu" : argument.description();
        String commandString = "/" + label + (parent.equals(argument) ? "" : " " + argument.name()) + (argument.usage().isEmpty() ? "" : " " + argument.usage());
        String commandFormattedString = " §7- " + argument.colorScheme().secondaryColor() + commandString + (description.isEmpty() ? "" : " §7" + argument.description());
        if (sender instanceof ConsoleCommandSender)
            sender.sendMessage(commandFormattedString);
        else {
            List<String> hover = new ArrayList<>();
            hover.add("§7Players Only: §f" + Style.tf(argument.playersOnly()));
            if (!argument.permission().isEmpty())
                hover.add("§7Has Permission: §f" + Style.tf(sender.hasPermission(argument.permission())));
            hover.add("");
            hover.add("§7§oClick to execute");

            TextComponent component = new TextComponent(commandFormattedString);
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(MiscUtils.arrayToString(hover)).create()));
            component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandString));
            ((Player) sender).sendMessage(component);
        }
    }

    /**
     * Returns whether or not the provided command is the same name as the provided alias
     * or has the provided alias in the provided commands alias list
     * @param command - The command you would like to check aliases for
     * @param alias - The alias you would like to check
     * @return whether or not the provided command is the same name as the provided alias
     */
    private boolean isAlias(Command command, String alias) {
        if (command.name().equalsIgnoreCase(alias))
            return true;
        for (String a : command.aliases()) {
            if (a.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs a check for the provided sender if they can execute the provided command
     * @param command - The command you would like to check
     * @param sender - The sender you would like to check
     * @return whether or not the provided sender can execute the provided command
     */
    private boolean canExecute(Command command, CommandSender sender) {
        if (!command.permission().isEmpty() && (!sender.hasPermission(command.permission())))
            sender.sendMessage("§cNo permission.");
        else if (sender instanceof ConsoleCommandSender && command.playersOnly())
            sender.sendMessage("§cOnly players can execute this command.");
        else if (sender instanceof Player && command.consoleOnly())
            sender.sendMessage("§cThis command can only be executed from the terminal.");
        else return true;
        return false;
    }

    /**
     * Registers the provided command
     * @param obj - The instance of the command
     */
    public static void addCommand(Object obj) {
        Arrays.stream(obj.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(Command.class))
                .collect(Collectors.toList()).forEach(method -> commands.put(obj, new Tuple<>(method, method.getAnnotation(Command.class))));
        if (Options.DEBUGGING.getBoolean())
            Bukkit.getLogger().info("Added command: " + obj.getClass().getSimpleName());
    }

    /**
     * Registers the provided command argument
     * @param parentClass - The class of the parent command
     * @param obj - The instance of the command argument
     */
    public static void addArgument(Class<?> parentClass, Object obj) {
        Command parent = null;
        for (Map.Entry<Object, Tuple<Method, Command>> entry : commands.entrySet()) {
            if (entry.getKey().getClass().equals(parentClass)) {
                parent = entry.getValue().getRight();
                break;
            }
        }
        if (parent == null)
            throw new IllegalStateException("Failed to add argument '" + obj.getClass().getSimpleName() + "', the parent seems to not be registered!");
        Method method = Arrays.stream(obj.getClass().getMethods())
                .filter(m -> m.isAnnotationPresent(Command.class))
                .findFirst().orElse(null);
        if (method == null)
            throw new IllegalStateException("Failed to register argument '" + obj.getClass().getSimpleName() + ", there is no method with @Command");
        Command command = method.getAnnotation(Command.class);
        List<TriTuple<Object, Method, Command>> arguments = CommandHandler.arguments.getOrDefault(parent, new ArrayList<>());;
        arguments.add(new TriTuple<>(obj, method, command));
        CommandHandler.arguments.put(parent, arguments);
        if (Options.DEBUGGING.getBoolean())
            Bukkit.getLogger().info("Registered argument '" + obj.getClass().getSimpleName() + "' with parent '" + parentClass.getSimpleName() + "'");
    }

    /**
     * Removes all registered commands and arguments
     */
    public static void removeAll() {
        for (Object obj : commands.keySet()) {
            remove(obj.getClass());
        }
    }

    /**
     * Removes the provided register command and it's registered arguments
     * @param clazz - The class of the command you would like to unregister
     */
    public static void remove(Class<?> clazz) {
        commands.entrySet().removeIf(entry -> entry.getKey().getClass().equals(clazz));
        for (List<TriTuple<Object, Method, Command>> triTuples : arguments.values()) {
            triTuples.removeIf(triTuple -> triTuple.getLeft().getClass().equals(clazz));
        }
        if (Options.DEBUGGING.getBoolean())
            Bukkit.getLogger().info("Removed command: " + clazz.getSimpleName());
    }
}