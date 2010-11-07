/**
 * You received this file as part of Finstruct - a tool for
 * the Finroc Framework.
 *
 * Copyright (C) 2010 Robotics Research Lab, University of Kaiserslautern
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.finroc.finstruct.util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * @author max
 *
 * List that only displays elements currently accepted by filter.
 *
 * Can be added as listener to other JComponents. Every time, these components change - updateListModel() will be called.
 */
public class FilteredList<T> extends JList implements ActionListener, CaretListener {

    /**
     * Owner of list needs to provide such a filter
     * Decides which elements are displayed - and in which order
     */
    public interface Filter<E> {

        /**
         * @return List rank (0-elements will be displayed first, 1 after that, 2 after that, etc... -1 will not be displayed)
         */
        public int accept(E t);
    }

    /** UID */
    private static final long serialVersionUID = 503946463400455305L;

    /** All list elements (not filtered yet) */
    private final ArrayList<T> allElements = new ArrayList<T>();

    /** Elements with filters applied */
    private final ArrayList<T> filteredElements = new ArrayList<T>();

    /** Temporary variables for updating list */
    private final ArrayList<SortedSet<T>> tempRankSets = new ArrayList<SortedSet<T>>();

    /** Temporary new list */
    private final ArrayList<T> tempNewList = new ArrayList<T>();

    /** Filter to use */
    private final Filter<T> filter;

    /** Data model listeners */
    private final ArrayList<ListDataListener> listener = new ArrayList<ListDataListener>();

    public FilteredList(List<T> allElements, Filter<T> filter) {
        this(filter);
        setElements(allElements);
    }

    public FilteredList(Filter<T> filter) {
        this.filter = filter;
        setModel(new Model());
    }

    /**
     * @param allElements New list of elements
     */
    public void setElements(List<T> allElements) {
        this.allElements.clear();
        this.allElements.addAll(allElements);
        updateListModel();
    }

    /**
     * updates list according to filter
     */
    private void updateListModel() {

        // cleat temporary lists
        for (SortedSet<T> tmpSet : tempRankSets) {
            tmpSet.clear();
        }

        // apply filter
        for (T e : allElements) {
            int rank = filter.accept(e);
            if (rank < 0) {
                continue;
            }
            while (tempRankSets.size() <= rank) {
                tempRankSets.add(new TreeSet<T>());
            }
            tempRankSets.get(rank).add(e);
        }

        // create new list
        tempNewList.clear();
        for (SortedSet<T> tmpSet : tempRankSets) {
            tempNewList.addAll(tmpSet);
        }

        // compare lists - and return if nothing changed
        if (tempNewList.size() == filteredElements.size()) {
            boolean diff = false;
            for (int i = 0; i < tempNewList.size(); i++) {
                if (!tempNewList.get(i).equals(filteredElements.get(i))) {
                    diff = true;
                    break;
                }
            }
            if (!diff) {
                return;
            }
        }


        filteredElements.clear();
        filteredElements.addAll(tempNewList);

        // notify listeners
        for (ListDataListener l : listener) {
            l.contentsChanged(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, filteredElements.size()));
        }
        if (filteredElements.size() > 0) {
            setSelectedIndex(0);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        updateListModel();
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        updateListModel();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getSelectedValue() {
        return (T)super.getSelectedValue();
    }

    /**
     * List model for JList
     */
    private class Model implements ListModel {

        @Override
        public int getSize() {
            return filteredElements.size();
        }

        @Override
        public Object getElementAt(int index) {
            return filteredElements.get(index);
        }

        @Override
        public void addListDataListener(ListDataListener l) {
            if (!listener.contains(l)) {
                listener.add(l);
            }
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
            listener.remove(l);
        }
    }
}
