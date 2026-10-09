package com.coordcast.app;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * An EditText that reports real paste actions.
 *
 * <p>Distributing a pasted value across the two fields has to key off an actual paste.
 * Guessing from "how many characters changed" does not work: an IME committing a word,
 * an autocorrect replacement, or a swipe-typing completion all arrive as one bulk edit
 * that never touched the clipboard.</p>
 *
 * <p>{@link #onTextContextMenuItem(int)} is the hook the text selection toolbar (and the
 * system paste shortcut) goes through, so it is the one reliable signal.</p>
 */
public class CoordEditText extends EditText {

    /** Notified just before the clipboard content is inserted. */
    public interface OnPaste {
        void onPaste();
    }

    private OnPaste onPaste;

    public CoordEditText(Context context) {
        super(context);
    }

    public CoordEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CoordEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnPaste(OnPaste listener) {
        this.onPaste = listener;
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (onPaste != null && (id == android.R.id.paste || id == android.R.id.pasteAsPlainText)) {
            onPaste.onPaste();
        }
        return super.onTextContextMenuItem(id);
    }
}
