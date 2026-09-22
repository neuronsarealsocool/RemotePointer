package systems.sieber.remotespotlight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.UUID;

public class RemoteListActivity extends AppCompatActivity {
    public static final String EXTRA_REMOTE_ID = "remoteId";
    public static final String EXTRA_REMOTE_NAME = "remoteName";
    public static final String EXTRA_EMPTY_REMOTE = "emptyRemote";
    public static final String EXTRA_DISABLE_AUTO_OPEN = "disableAutoOpen";
    public static final String EXTRA_RETURN_TO_REMOTE_LIST = "returnToRemoteList";

    private static final int REQUEST_CONTROL = 1;

    private List<RemoteRepository.Remote> remotes;
    private RemoteAdapter remoteAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        remotes = RemoteRepository.load(this);
        remoteAdapter = new RemoteAdapter();

        ListView remoteList = findViewById(R.id.listViewRemotes);
        remoteList.setAdapter(remoteAdapter);
        remoteList.setOnItemClickListener((parent, view, position, id) -> openRemote(remotes.get(position)));

        FloatingActionButton addRemote = findViewById(R.id.buttonAddRemote);
        addRemote.setOnClickListener(view -> showAddRemoteDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(remoteAdapter != null) {
            remotes = RemoteRepository.load(this);
            remoteAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_remote_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_connection_settings) {
            openConnectionSetup(null);
            return true;
        }
        if(item.getItemId() == R.id.action_information) {
            startActivity(new Intent(this, HelpActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode != REQUEST_CONTROL || resultCode != Activity.RESULT_OK || data == null) return;

        ControlActivity.messageType result =
                (ControlActivity.messageType) data.getSerializableExtra("result");
        if(result == null || result == ControlActivity.messageType.normalExit) return;

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setTitle(R.string.connfailed_title);
        if(result == ControlActivity.messageType.authFailed) {
            dialog.setMessage(getString(R.string.authfailed_text));
        } else if(result == ControlActivity.messageType.connectionFailed) {
            dialog.setMessage(getString(R.string.connfailed_text));
        } else {
            dialog.setMessage(getString(R.string.connclosed_text));
        }
        dialog.setIcon(getDrawable(R.drawable.fail));
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok),
                (dialogInterface, which) -> dialogInterface.dismiss());
        dialog.show();
    }

    private void showAddRemoteDialog() {
        EditText nameInput = new EditText(this);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        nameInput.setPadding(padding, 0, padding, 0);
        nameInput.setHint(R.string.remote_name);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setSingleLine(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.new_remote)
                .setView(nameInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String name = nameInput.getText().toString().trim();
                if(name.isEmpty()) {
                    nameInput.setError(getString(R.string.remote_name));
                    return;
                }
                RemoteRepository.Remote remote = new RemoteRepository.Remote(
                        UUID.randomUUID().toString(), name, false);
                remotes.add(remote);
                RemoteRepository.save(this, remotes);
                remoteAdapter.notifyDataSetChanged();
                dialog.dismiss();
                openRemote(remote);
            });
            nameInput.requestFocus();
            if(dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.show();
    }

    private void openRemote(RemoteRepository.Remote remote) {
        SharedPreferences settings = getSharedPreferences(ConnectActivity.PREFS_NAME, 0);
        String address = settings.getString("address", "").trim();
        String authCode = settings.getString("authCode", "").trim();
        if(address.isEmpty() || authCode.isEmpty()) {
            openConnectionSetup(remote);
            return;
        }

        Intent controlIntent = new Intent(this, ControlActivity.class);
        controlIntent.putExtra("address", address);
        controlIntent.putExtra("port", settings.getInt("port", 4444));
        controlIntent.putExtra("authCode", authCode);
        putRemoteExtras(controlIntent, remote);
        startActivityForResult(controlIntent, REQUEST_CONTROL);
    }

    private void openConnectionSetup(RemoteRepository.Remote remote) {
        Intent connectIntent = new Intent(this, ConnectActivity.class);
        connectIntent.putExtra(EXTRA_DISABLE_AUTO_OPEN, true);
        connectIntent.putExtra(EXTRA_RETURN_TO_REMOTE_LIST, true);
        if(remote != null) putRemoteExtras(connectIntent, remote);
        startActivity(connectIntent);
    }

    private void putRemoteExtras(Intent intent, RemoteRepository.Remote remote) {
        intent.putExtra(EXTRA_REMOTE_ID, remote.id);
        intent.putExtra(EXTRA_REMOTE_NAME, remote.name);
        intent.putExtra(EXTRA_EMPTY_REMOTE, !remote.builtInMouse);
    }

    private final class RemoteAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(RemoteListActivity.this);

        @Override
        public int getCount() {
            return remotes.size();
        }

        @Override
        public RemoteRepository.Remote getItem(int position) {
            return remotes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if(view == null) view = inflater.inflate(R.layout.item_remote, parent, false);

            RemoteRepository.Remote remote = getItem(position);
            ((TextView) view.findViewById(R.id.textViewRemoteName)).setText(remote.name);
            ((TextView) view.findViewById(R.id.textViewRemoteDescription)).setText(
                    remote.builtInMouse
                            ? R.string.mouse_remote_description
                            : R.string.custom_remote_description
            );
            ((ImageView) view.findViewById(R.id.imageViewRemoteIcon)).setImageResource(
                    remote.builtInMouse
                            ? R.drawable.ic_mouse_dynamic_24dp
                            : R.drawable.ic_remote_controls_24dp
            );
            return view;
        }
    }
}
