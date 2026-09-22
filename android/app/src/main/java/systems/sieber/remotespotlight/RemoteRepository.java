package systems.sieber.remotespotlight;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class RemoteRepository {
    private static final String PREF_REMOTES = "remoteDefinitionsV1";
    private static final String LEGACY_REMOTE_NAMES = "remoteNames";

    private RemoteRepository() { }

    static List<Remote> load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(ConnectActivity.PREFS_NAME, 0);
        String savedRemotes = preferences.getString(PREF_REMOTES, "");
        List<Remote> remotes = new ArrayList<>();

        if(!savedRemotes.isEmpty()) {
            try {
                JSONArray array = new JSONArray(savedRemotes);
                for(int i = 0; i < array.length(); i++) {
                    Remote remote = Remote.fromJson(array.getJSONObject(i));
                    if(remote != null) remotes.add(remote);
                }
            } catch(JSONException ignored) {
                remotes.clear();
            }
        }

        if(remotes.isEmpty()) {
            migrateLegacyRemotes(context, preferences.getString(LEGACY_REMOTE_NAMES, ""), remotes);
            save(context, remotes);
        }
        return remotes;
    }

    private static void migrateLegacyRemotes(Context context, String savedNames, List<Remote> remotes) {
        if(!savedNames.isEmpty()) {
            try {
                JSONArray names = new JSONArray(savedNames);
                for(int i = 0; i < names.length(); i++) {
                    String name = names.optString(i).trim();
                    if(!name.isEmpty()) remotes.add(new Remote(
                            i == 0 ? "mouse-default" : UUID.randomUUID().toString(),
                            name,
                            i == 0
                    ));
                }
            } catch(JSONException ignored) {
                remotes.clear();
            }
        }
        if(remotes.isEmpty()) {
            remotes.add(new Remote("mouse-default", context.getString(R.string.mouse_remote), true));
        }
    }

    static void save(Context context, List<Remote> remotes) {
        JSONArray array = new JSONArray();
        for(Remote remote : remotes) array.put(remote.toJson());
        context.getSharedPreferences(ConnectActivity.PREFS_NAME, 0)
                .edit()
                .putString(PREF_REMOTES, array.toString())
                .apply();
    }

    static Remote find(Context context, String remoteId) {
        if(remoteId == null) return null;
        for(Remote remote : load(context)) {
            if(remote.id.equals(remoteId)) return remote;
        }
        return null;
    }

    static void saveRemote(Context context, Remote updatedRemote) {
        List<Remote> remotes = load(context);
        for(int i = 0; i < remotes.size(); i++) {
            if(remotes.get(i).id.equals(updatedRemote.id)) {
                remotes.set(i, updatedRemote);
                save(context, remotes);
                return;
            }
        }
    }

    static final class Remote {
        final String id;
        String name;
        final boolean builtInMouse;
        final List<RemoteButton> buttons = new ArrayList<>();

        Remote(String id, String name, boolean builtInMouse) {
            this.id = id;
            this.name = name;
            this.builtInMouse = builtInMouse;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray controls = new JSONArray();
            for(RemoteButton button : buttons) controls.put(button.toJson());
            try {
                object.put("id", id);
                object.put("name", name);
                object.put("builtInMouse", builtInMouse);
                object.put("buttons", controls);
            } catch(JSONException ignored) { }
            return object;
        }

        static Remote fromJson(JSONObject object) {
            String id = object.optString("id").trim();
            String name = object.optString("name").trim();
            if(id.isEmpty() || name.isEmpty()) return null;

            Remote remote = new Remote(id, name, object.optBoolean("builtInMouse", false));
            JSONArray buttons = object.optJSONArray("buttons");
            if(buttons != null) {
                for(int i = 0; i < buttons.length(); i++) {
                    JSONObject buttonJson = buttons.optJSONObject(i);
                    if(buttonJson != null) remote.buttons.add(RemoteButton.fromJson(buttonJson));
                }
            }
            return remote;
        }
    }

    static final class RemoteButton {
        final String id;
        String label;
        String action;
        String value;
        float x;
        float y;
        float width;
        float height;
        int color;
        String shape;

        RemoteButton() {
            this(UUID.randomUUID().toString());
        }

        private RemoteButton(String id) {
            this.id = id;
            label = "Button";
            action = "Text";
            value = "a";
            x = 0.30f;
            y = 0.24f;
            width = 0.40f;
            height = 0.16f;
            color = Color.rgb(55, 71, 79);
            shape = "Rounded";
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("label", label);
                object.put("action", action);
                object.put("value", value);
                object.put("x", x);
                object.put("y", y);
                object.put("width", width);
                object.put("height", height);
                object.put("color", color);
                object.put("shape", shape);
            } catch(JSONException ignored) { }
            return object;
        }

        static RemoteButton fromJson(JSONObject object) {
            String id = object.optString("id", UUID.randomUUID().toString());
            RemoteButton button = new RemoteButton(id);
            button.label = object.optString("label", "Button");
            button.action = object.optString("action", "Text");
            button.value = object.optString("value", "a");
            button.x = (float) object.optDouble("x", 0.30);
            button.y = (float) object.optDouble("y", 0.24);
            button.width = (float) object.optDouble("width", 0.40);
            button.height = (float) object.optDouble("height", 0.16);
            button.color = object.optInt("color", Color.rgb(55, 71, 79));
            button.shape = object.optString("shape", "Rounded");
            return button;
        }
    }
}
