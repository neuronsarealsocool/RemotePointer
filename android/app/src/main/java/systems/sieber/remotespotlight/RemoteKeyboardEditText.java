package systems.sieber.remotespotlight;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.widget.AppCompatEditText;

public class RemoteKeyboardEditText extends AppCompatEditText {
    private static final String EMPTY_INPUT_SENTINEL = "\u200B";

    private Runnable emptyBackspaceListener;
    private boolean restoringSentinel;
    private boolean sentinelWasOnlyText;

    public RemoteKeyboardEditText(Context context) {
        super(context);
        initialize();
    }

    public RemoteKeyboardEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public RemoteKeyboardEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setText(EMPTY_INPUT_SENTINEL);
        setSelection(length());
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                sentinelWasOnlyText = EMPTY_INPUT_SENTINEL.contentEquals(s);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if(restoringSentinel || s.length() != 0) return;

                if(sentinelWasOnlyText && emptyBackspaceListener != null) {
                    emptyBackspaceListener.run();
                }
                restoringSentinel = true;
                s.append(EMPTY_INPUT_SENTINEL);
                setSelection(s.length());
                restoringSentinel = false;
                refreshInputConnection();
            }
        });
    }

    public void setOnEmptyBackspaceListener(Runnable listener) {
        emptyBackspaceListener = listener;
    }

    private boolean sendEmptyBackspace() {
        if(hasUserText() || emptyBackspaceListener == null) return false;

        emptyBackspaceListener.run();
        refreshInputConnection();
        return true;
    }

    private void refreshInputConnection() {
        post(() -> {
            InputMethodManager inputMethodManager =
                    (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if(inputMethodManager != null && isFocused()) {
                inputMethodManager.restartInput(this);
            }
        });
    }

    public String withoutSentinel(CharSequence text) {
        return text.toString().replace(EMPTY_INPUT_SENTINEL, "");
    }

    public int userCodePointCount(CharSequence text) {
        String userText = withoutSentinel(text);
        return userText.codePointCount(0, userText.length());
    }

    public boolean hasUserText() {
        return !withoutSentinel(getText()).isEmpty();
    }

    public void clearUserText() {
        setText(EMPTY_INPUT_SENTINEL);
        setSelection(length());
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if(getText() != null
                && getText().toString().startsWith(EMPTY_INPUT_SENTINEL)
                && (selStart == 0 || selEnd == 0)) {
            setSelection(Math.max(1, selStart), Math.max(1, selEnd));
            return;
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection target = super.onCreateInputConnection(outAttrs);
        if(target == null) return null;

        return new InputConnectionWrapper(target, false) {
            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if(beforeLength > 0 && sendEmptyBackspace()) return true;
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                if(beforeLength > 0 && sendEmptyBackspace()) return true;
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if(event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_DEL
                        && sendEmptyBackspace()) {
                    return true;
                }
                return super.sendKeyEvent(event);
            }
        };
    }
}
