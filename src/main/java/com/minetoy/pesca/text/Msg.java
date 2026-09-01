package com.minetoy.pesca.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code messages.yml}, runs every string through {@link SmallCaps}, and parses
 * it as MiniMessage.
 *
 * <p>Messages reference the shared category prefixes through a {@code <pre:name>} tag,
 * e.g. {@code <pre:error>}, so the icon/label/bullet block is defined once in the
 * {@code prefixes:} section rather than repeated on every line.
 */
public final class Msg {

    private final JavaPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Map<String, Component> prefixes = new HashMap<>();

    private TagResolver preResolver = TagResolver.empty();
    private boolean smallCaps = true;

    public Msg(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(boolean smallCapsEnabled) {
        this.smallCaps = smallCapsEnabled;
        messages.clear();
        lists.clear();
        prefixes.clear();

        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);

        // Defaults from the jar, so a message added in a later version still resolves
        // against an older on-disk messages.yml instead of rendering as its key.
        YamlConfiguration defaults = null;
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                user.setDefaults(defaults);
                user.options().copyDefaults(true);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("no se pudo leer messages.yml del jar: " + e.getMessage());
        }

        for (String key : user.getKeys(true)) {
            if (user.isList(key)) {
                List<String> raw = user.getStringList(key);
                List<String> cooked = new ArrayList<>(raw.size());
                for (String s : raw) {
                    cooked.add(SmallCaps.apply(s, smallCaps));
                }
                lists.put(key, cooked);
            } else if (user.isString(key)) {
                messages.put(key, SmallCaps.apply(user.getString(key), smallCaps));
            }
        }

        for (Map.Entry<String, String> e : messages.entrySet()) {
            if (e.getKey().startsWith("prefixes.")) {
                prefixes.put(e.getKey().substring("prefixes.".length()), mm.deserialize(e.getValue()));
            }
        }

        preResolver = TagResolver.resolver("pre", (args, ctx) -> {
            String name = args.popOr("se esperaba el nombre del prefijo").value();
            Component c = prefixes.get(name);
            return Tag.selfClosingInserting(c == null ? Component.empty() : c);
        });
    }

    public boolean smallCaps() {
        return smallCaps;
    }

    /**
     * Small-caps a runtime string that is about to be injected as an unparsed
     * placeholder. Values normally bypass the messages.yml conversion, so words the
     * plugin generates itself ("activo", "sin definir") need this to match the rest.
     */
    public String value(String raw) {
        return SmallCaps.apply(raw, smallCaps);
    }

    /** Runs an arbitrary config string (a fish name, a shop item name) through the same pipeline. */
    public Component parse(String raw, TagResolver... resolvers) {
        return mm.deserialize(SmallCaps.apply(raw, smallCaps), merge(resolvers));
    }

    /** {@link #parse} over a list of raw config strings. */
    public List<Component> parseAll(List<String> raw, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            out.add(parse(s, resolvers));
        }
        return out;
    }

    /** Parses a string that is already small-capped (or deliberately isn't). */
    public Component parseRaw(String raw, TagResolver... resolvers) {
        return mm.deserialize(raw, merge(resolvers));
    }

    public Component get(String key, TagResolver... resolvers) {
        String raw = messages.get(key);
        if (raw == null) {
            return Component.text("<" + key + ">");
        }
        return mm.deserialize(raw, merge(resolvers));
    }

    public List<Component> getList(String key, TagResolver... resolvers) {
        List<String> raw = lists.get(key);
        List<Component> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        TagResolver merged = merge(resolvers);
        for (String s : raw) {
            out.add(mm.deserialize(s, merged));
        }
        return out;
    }

    public boolean has(String key) {
        return messages.containsKey(key) || lists.containsKey(key);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        String raw = messages.get(key);
        if (raw != null && raw.isEmpty()) {
            return; // an empty message is a deliberate "say nothing"
        }
        to.sendMessage(get(key, resolvers));
    }

    public void sendList(CommandSender to, String key, TagResolver... resolvers) {
        for (Component c : getList(key, resolvers)) {
            to.sendMessage(c);
        }
    }

    private TagResolver merge(TagResolver... resolvers) {
        if (resolvers == null || resolvers.length == 0) {
            return preResolver;
        }
        TagResolver.Builder b = TagResolver.builder();
        b.resolver(preResolver);
        for (TagResolver r : resolvers) {
            if (r != null) {
                b.resolver(r);
            }
        }
        return b.build();
    }

    /** Shorthand for an untrusted string (player names, item names typed by users). */
    public static TagResolver p(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "" : value);
    }

    /** Shorthand for a pre-built component (a coloured fish name, a rarity label). */
    public static TagResolver c(String name, Component value) {
        return Placeholder.component(name, value == null ? Component.empty() : value);
    }
}
