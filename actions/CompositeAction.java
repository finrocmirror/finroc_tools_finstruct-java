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

import java.util.ArrayList;

/**
 * @author Max Reichardt
 *
 * Composite action.
 * Contains an arbitrary number actions that will be executed sequentially.
 *
 *  If one action fails, tries to undo any actions that have already been performed.
 */
public class CompositeAction extends FinstructAction {

    /** Wherever this special action is inserted in composite action list, all actions before will be checked for success before continuing with actions after */
    public static final CompositeAction CHECK_SUCCESS_BEFORE_CONTINUE = new CompositeAction("");

    /**
     * @param menuDescription Description for edit menu
     */
    public CompositeAction(String menuDescription) {
        this.menuDescription = menuDescription;
    }

    @Override
    protected void executeImplementation() throws Exception {
        executedActions.clear();
        try {
            for (FinstructAction action : actions) {
                if (action == CHECK_SUCCESS_BEFORE_CONTINUE) {
                    String result = checkSuccess();
                    if (result.length() > 0) {
                        if (tryUndoExecutedActionsOnError) {
                            CompositeAction undoStepsAction = (CompositeAction)getUndoActionImplementation();
                            undoStepsAction.menuDescription = "Undoing already performed actions";
                            undoStepsAction.executeAsyncNoException();
                            String undoResult = undoStepsAction.checkSuccess();
                            if (undoResult.length() > 0) {
                                result += "\n\nWarning: " + undoResult;
                            }
                        }
                        throw new Exception(result);
                    }
                }
                action.executeAsync();
                executedActions.add(action);
            }
        } catch (Exception e) {
            String result = menuDescription + " failed:\n  " + e.getMessage();
            if (tryUndoExecutedActionsOnError) {
                CompositeAction undoStepsAction = (CompositeAction)getUndoActionImplementation();
                undoStepsAction.menuDescription = "Undoing already performed actions";
                undoStepsAction.executeAsyncNoException();
                String undoResult = undoStepsAction.checkSuccess();
                if (undoResult.length() > 0) {
                    result += "\n\nWarning: " + undoResult;
                }
            }
            throw new Exception(result);
        }
    }

    @Override
    protected String checkSuccessImplementation() {
        StringBuilder sb = new StringBuilder();
        int success = 0;
        for (FinstructAction action : executedActions) {
            String result = action.checkSuccessAsync();
            if (result == null) {
                return null;
            }
            if (result.length() > 0) {
                sb.append("\n  ").append(result);
            } else {
                success++;
            }
        }
        return sb.length() == 0 ? "" : (menuDescription + " failed (" + (actions.size() - success) + "/" + actions.size() + "): " + sb.toString());
    }

    @Override
    protected FinstructAction getUndoActionImplementation() {
        CompositeAction undoAction = new CompositeAction("Undo " + menuDescription);
        undoAction.tryUndoExecutedActionsOnError = false;
        for (FinstructAction action : executedActions) {
            if (action.checkSuccess().length() == 0) {
                undoAction.actions.add(0, action.getUndoAction());
            }
        }
        return undoAction;
    }

    @Override
    public String getDescriptionForEditMenu() {
        return menuDescription;
    }

    /**
     * @param description The new description
     */
    public void setDescriptionForEditMenu(String description) {
        this.menuDescription = description;
    }

    /**
     * @return All actions in composite action in the order they will be executed
     */
    public ArrayList<FinstructAction> getActions() {
        return actions;
    }

    @Override
    public String toString() {
        return alternativeDescription;
    }

    /**
     * @param newDescription Description for popup dialog
     */
    public void setAlternativeDescription(String newDescription) {
        alternativeDescription = newDescription;
    }

    /**
     * @return Description for popup dialog
     */
    public String getAlternativeDescription() {
        return alternativeDescription;
    }


    /** All actions in composite action in the order they will be executed */
    private final ArrayList<FinstructAction> actions = new ArrayList<FinstructAction>();

    /** All actions that have been executed */
    private final ArrayList<FinstructAction> executedActions = new ArrayList<FinstructAction>();

    /** Description for edit menu */
    private String menuDescription = "Composite Action";

    /** Description for popup dialog */
    private String alternativeDescription = "No description";

    /** Undo executedActions on one unsuccessful action? */
    private boolean tryUndoExecutedActionsOnError = true;
}
