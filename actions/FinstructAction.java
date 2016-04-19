//
// You received this file as part of Finroc
// A framework for intelligent robot control
//
// Copyright (C) Finroc GbR (finroc.org)
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//----------------------------------------------------------------------
package org.finroc.tools.finstruct.actions;

import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;


/**
 * @author Max Reichardt
 *
 * Abstract base interface for undoable actions in finstruct.
 */
public abstract class FinstructAction implements ActionListener {

    /** Default timeout in ms */
    public final long DEFAULT_TIMEOUT = 1000;

    /** Separator for model node links used in actions */
    public static final char LINK_SEPARATOR = '/';


    /**
     * Check success of action.
     * Blocks until success was determined - or timeout was reached.
     *
     * @param timeout Timeout (in ms)
     * @return "" if action was successful. Error message if action was not successful.
     */
    public final String checkSuccess(long timeout) {
        if (failMessage != null) {
            return failMessage;
        }
        timeoutAt = System.currentTimeMillis() + timeout;
        if (failMessage == null) {
            checkSuccessAsync();
            if (failMessage == null) {
                EventQueue eq = Toolkit.getDefaultToolkit().getSystemEventQueue();
                secondaryLoop = eq.createSecondaryLoop();
                timer.start();
                if (!secondaryLoop.enter()) {
                    failMessage = "Failed to enter secondary loop thread";
                }
            }
        }
        assert(failMessage != null);
        return failMessage;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == timer) {
            checkSuccessAsync();
            if (failMessage != null) {
                timer.stop();
                secondaryLoop.exit();
            } else if (System.currentTimeMillis() > timeoutAt) {
                failMessage = "Timeout (Console output of the Finroc Application may contain information on what went wrong)";
                secondaryLoop.exit();
            }
        }
    }


    /**
     * CheckSuccess with default timeout.
     *
     * @return "" if action was successful. Error message if action was not successful.
     */
    public final String checkSuccess() {
        return checkSuccess(getDefaultTimeout());
    }

    /**
     * Check success of action without blocking
     *
     * @return null if success was not determined yet. "" if action was successful. Error message if action was not successful.
     */
    public final String checkSuccessAsync() {
        if (failMessage == null) {
            failMessage = checkSuccessImplementation();
        }
        return failMessage;
    }

    /**
     * Execute/Redo action.
     * Blocks until success was checked.
     *
     * @param timeout Timeout (in ms)
     * @throws Throws exception if executing action failed. Message is error message.
     */
    public final void execute(long timeout) throws Exception {
        executeAsync();
        String result = checkSuccess(timeout);
        if (result.length() > 0) {
            throw new Exception(result);
        }
    }

    /**
     * execute with default timeout.
     *
     * @throws Throws exception if executing action failed. Message is error message.
     */
    public final void execute() throws Exception {
        execute(getDefaultTimeout());
    }

    /**
     * Execute/Redo action - possibly without checking result.
     * Checking successful execution of action must be done via checkSuccess unless an exception is thrown.
     *
     * @throws Throws exception if executing action failed immediately. Message is error message.
     */
    public final void executeAsync() throws Exception {
        failMessage = null;
        executeTimestamp = System.currentTimeMillis();
        try {
            executeImplementation();
        } catch (Exception e) {
            failMessage = e.getMessage();
            throw e;
        }
    }

    /**
     * Execute/Redo action without checking result.
     */
    public final void executeAsyncNoException() {
        try {
            executeAsync();
        } catch (Exception e) {}
    }

    /**
     * (may be overridden)
     *
     * @return Default timeout of action in ms
     */
    public long getDefaultTimeout() {
        return DEFAULT_TIMEOUT;
    }

    /**
     * @return Description of action as displayed in edit menu
     */
    public abstract String getDescriptionForEditMenu();

    /**
     * @param fullLink Full link to model node
     * @return Shorter link as to be used for edit menu
     */
    static public String getReadableLinkForMenu(String fullLink) {
        String[] parts = fullLink.split("" + LINK_SEPARATOR);
        if (parts.length >= 3) {
            return parts[parts.length - 3] + LINK_SEPARATOR + parts[parts.length - 2] + LINK_SEPARATOR + parts[parts.length - 1];
        } else {
            return fullLink;
        }
    }

    /**
     * Must only be called after action was successfully executed.
     *
     * @return Undo action for this action
     * @throws RuntimeException Thrown if action was not fully executed/evaluated yet .
     */
    public final FinstructAction getUndoAction() {
        if (executeTimestamp == 0) {
            throw new RuntimeException("Execute action first");
        }
        if (failMessage == null) {
            throw new RuntimeException("Call after action was executed and result determined.");
        }
        if (failMessage.length() > 0) {
            throw new RuntimeException("Cannot get undo action for failed action");
        }
        return getUndoActionImplementation();
    }


    protected FinstructAction() {
        timer.setRepeats(true);
    }

    /**
     * Execute/Redo action - possibly without checking result.
     * Checking result of action can be done via checkSuccess methods.
     * May block if action consists of several sequential steps.
     *
     * @throws Throws exception if executing action failed immediately. Message is error message.
     */
    protected abstract void executeImplementation() throws Exception;

    /**
     * Checks success of executing action.
     * Must not block!
     *
     * @return null if success was not determined yet. "" if action was successful. Error message if action was not successful.
     */
    protected abstract String checkSuccessImplementation();

    /**
     * (is only called after action was successfully executed)
     *
     * @return Undo action for this action
     */
    protected abstract FinstructAction getUndoActionImplementation();


    /** Did action fail? */
    private volatile String failMessage;

    /** Timestamp when action was last executed */
    private long executeTimestamp;

    /** Timestamp for timeout of current synchronous call */
    private long timeoutAt;

    /** Secondary loop for blocking execution */
    private SecondaryLoop secondaryLoop;

    /** Timer to check for success in blocking methods */
    private final Timer timer = new Timer(10, this);

}
