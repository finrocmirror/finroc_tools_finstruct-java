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
package org.finroc.tools.finstruct.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.finroc.core.datatype.Duration;
import org.finroc.core.remote.ParameterDefinition;
import org.finroc.core.remote.RemoteConnectOptions;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.remote.RemoteType;
import org.finroc.core.remote.RemoteTypeConversion;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.SmartConnecting;
import org.finroc.tools.finstruct.actions.ConnectAction;
import org.finroc.tools.finstruct.actions.FinstructAction;
import org.finroc.tools.gui.util.gui.MDialog;
import org.rrlib.serialization.Serialization;
import org.rrlib.serialization.StringInputStream;
import org.rrlib.serialization.rtti.GenericObject;

/**
 * @author Max Reichardt
 *
 * Dialog to edit options of connectors to create
 */
public class CreateConnectorOptionsDialog extends MDialog {

    public CreateConnectorOptionsDialog(Frame owner) {
        super(owner, true);
    }

    /**
     * @param actions Actions for connectors to create - and whose options to modify/set
     * @throws Throws Exception if any action is invalid/impossible to complete
     */
    @SuppressWarnings("unchecked")
    public void show(List<FinstructAction> actions) throws Exception {

        // Create/collect action batches
        CreateConnectActionBatch noConversionBatch = null;
        boolean tcpConnectors = false;
        for (FinstructAction action : actions) {
            if (action instanceof ConnectAction) {

                // Lookup types (note: Ports may not exist yet)
                ConnectAction connectAction = (ConnectAction)action;
                RemoteRuntime runtime1 = FinstructAction.findRemoteRuntime(connectAction.getSourceLink());
                RemoteRuntime runtime2 = FinstructAction.findRemoteRuntime(connectAction.getDestinationLink());
                if (runtime1 == null) {
                    throw new Exception("Connection to remote runtime environment for port '" + connectAction.getSourceLink() + "' unavailable");
                }
                if (runtime2 == null) {
                    throw new Exception("Connection to remote runtime environment for port '" + connectAction.getDestinationLink() + "' unavailable");
                }
                RemoteType type1 = runtime1.getRemoteType(connectAction.getSourceTypeName());
                RemoteType type2 = runtime2.getRemoteType(connectAction.getDestinationTypeName());
                if (type1 == null) {
                    throw new Exception("Data type '" + connectAction.getSourceTypeName() + "' unavailable in '" + runtime1.getQualifiedLink());
                }
                if (type2 == null) {
                    throw new Exception("Data type '" + connectAction.getDestinationTypeName() + "' unavailable in '" + runtime2.getQualifiedLink());
                }
                boolean tcpConnector = runtime1 != runtime2;
                tcpConnectors |= tcpConnector;

                if (type1 == type2 && (!tcpConnector)) {
                    if (noConversionBatch == null) {
                        noConversionBatch = new CreateConnectActionBatch();
                        noConversionBatch.sourceType = type1;
                        noConversionBatch.destinationType = type2;
                        noConversionBatch.sourceRuntime = runtime1;
                        noConversionBatch.destinationRuntime = runtime2;
                        noConversionBatch.tcpConnector = false;
                        actionBatches.add(noConversionBatch);
                    }
                    noConversionBatch.actions.add(connectAction);
                    continue;
                }

                // Add action to a batch - possibly create one
                boolean added = false;
                for (CreateConnectActionBatch batch : actionBatches) {
                    if (batch.sourceType == type1 && batch.destinationType == type2 && tcpConnector == batch.tcpConnector) {
                        batch.actions.add(connectAction);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    CreateConnectActionBatch batch = new CreateConnectActionBatch();
                    batch.actions.add(connectAction);
                    batch.sourceType = type1;
                    batch.destinationType = type2;
                    batch.sourceRuntime = runtime1;
                    batch.destinationRuntime = runtime2;
                    batch.tcpConnector = tcpConnector;
                    actionBatches.add(batch);
                }
            }
        }

        // Create UI
        this.setTitle("Connector Options");
        getContentPane().setLayout(new BorderLayout());

        // Options at the top
        JPanel flagPanel = new JPanel();
        flagPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Connector Options"));
        reconnect = new JCheckBox("Reconnect");
        optional = new JCheckBox("Optional");
        schedulingNeutral = new JCheckBox("Scheduling-neutral");
        flagPanel.add(reconnect);
        flagPanel.add(optional);
        flagPanel.add(schedulingNeutral);
        if (tcpConnectors) {
            JPanel minUpdate = new JPanel();
            minUpdate.setBorder(BorderFactory.createTitledBorder("Min. Network Update Interval [ms]"));
            minNetworkUpdateInterval = new JTextField();
            minNetworkUpdateInterval.setText("0");
            minUpdate.add(minNetworkUpdateInterval);
            flagPanel.add(minUpdate);
        }

        getContentPane().add(flagPanel, BorderLayout.NORTH);

        JPanel conversionsPanel = new JPanel();
        conversionsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Type Conversion"));
        conversionsPanel.setLayout(new BoxLayout(conversionsPanel, BoxLayout.Y_AXIS));

        // Action batches
        String ARROW_LABEL_STRING = "<html><h1>&#10145;</h1></html>";
        for (CreateConnectActionBatch batch : actionBatches) {

            JPanel mainPanel = new JPanel();
            mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            StringBuilder sources = new StringBuilder();
            StringBuilder destinations = new StringBuilder();
            for (ConnectAction action : batch.actions) {
                if (action != batch.actions.get(0)) {
                    sources.append('\n');
                    destinations.append('\n');
                }
                sources.append(formatPortLink(action.getSourceLink()));
                destinations.append(formatPortLink(action.getDestinationLink()));
            }

            mainPanel.add(new JLabel(sources.toString()));
            mainPanel.add(new JLabel(ARROW_LABEL_STRING));
            if (batch != noConversionBatch) {
                int conversions = batch.tcpConnector ? 2 : 1;
                batch.conversionOptions = new JComboBox[conversions];
                batch.conversionParameterLabels = new JLabel[conversions][];
                batch.conversionParameterValues = new JTextField[conversions][];

                List<SmartConnecting.StreamableType> streamableTypes = batch.tcpConnector ? SmartConnecting.getStreamableTypes(batch.sourceRuntime, batch.sourceType, batch.destinationRuntime, batch.destinationType) : null;
                if (batch.tcpConnector && streamableTypes.size() == 0) {
                    throw new Exception("No streamable type available");
                }


                for (int i = 0; i < conversions; i++) {
                    JPanel conversionPanel = new JPanel();
                    conversionPanel.setLayout(new BorderLayout());
                    String conversionTitle = "";
                    if (!batch.tcpConnector) {
                        batch.conversionOptions[i] = new JComboBox<RemoteConnectOptions>(batch.sourceRuntime.getConversionOptions(batch.sourceType, batch.destinationType, false).toArray(new RemoteConnectOptions[0]));
                        conversionTitle = batch.sourceType.getName() + "  \u27a1  " + batch.destinationType.getName();
                    } else if (i == 0) {
                        batch.conversionOptions[i] = new JComboBox<RemoteConnectOptions>(batch.sourceRuntime.getConversionOptions(batch.sourceType, batch.sourceRuntime.getRemoteType(streamableTypes.get(0).name), false).toArray(new RemoteConnectOptions[0]));
                        conversionTitle = batch.sourceType.getName() + "  \u27a1";
                    } else {
                        batch.conversionOptions[i] = new JComboBox<RemoteConnectOptions>(batch.destinationRuntime.getConversionOptions(batch.destinationRuntime.getRemoteType(streamableTypes.get(0).name), batch.destinationType, false).toArray(new RemoteConnectOptions[0]));
                        conversionTitle = "\u27a1  " + batch.destinationType.getName();
                    }
                    if (batch.conversionOptions[i].getModel().getSize() == 0) {
                        throw new Exception("No conversion options available");
                    }
                    conversionPanel.setBorder(BorderFactory.createTitledBorder(conversionTitle));
                    batch.conversionOptions[i].setSelectedIndex(0);
                    batch.conversionOptions[i].addActionListener(this);
                    conversionPanel.add(batch.conversionOptions[i], BorderLayout.NORTH);
                    JPanel parameterPanel = new JPanel();
                    conversionPanel.add(parameterPanel, BorderLayout.CENTER);
                    parameterPanel.setLayout(new GridLayout(2, 2));
                    batch.conversionParameterLabels[i] = new JLabel[2];
                    batch.conversionParameterValues[i] = new JTextField[2];
                    for (int j = 0; j < 2; j++) {
                        batch.conversionParameterLabels[i][j] = new JLabel();
                        parameterPanel.add(batch.conversionParameterLabels[i][j]);
                        batch.conversionParameterValues[i][j] = new JTextField();
                        parameterPanel.add(batch.conversionParameterValues[i][j]);
                    }
                    mainPanel.add(conversionPanel);

                    if (i == 0 && conversions == 2) {
                        mainPanel.add(new JLabel(ARROW_LABEL_STRING));
                        JPanel streamType = new JPanel();
                        streamType.setBorder(BorderFactory.createTitledBorder("Serialized Type"));
                        batch.streamedType = new JComboBox<>(streamableTypes.toArray(new SmartConnecting.StreamableType[0]));
                        batch.streamedType.setSelectedIndex(0);
                        batch.streamedType.addActionListener(this);
                        streamType.add(batch.streamedType);
                        mainPanel.add(streamType);
                        mainPanel.add(new JLabel(ARROW_LABEL_STRING));
                    }

                    batch.updateParameters(i);
                }

                mainPanel.add(new JLabel(ARROW_LABEL_STRING));
            }
            mainPanel.add(new JLabel(destinations.toString()));
            conversionsPanel.add(mainPanel);
        }

        if (conversionsPanel.getComponentCount() > 0) {
            getContentPane().add(conversionsPanel, BorderLayout.CENTER);
        }

        // Buttons at the bottom
        JPanel buttonPanel = new JPanel();
        cancel = createButton("Cancel", buttonPanel);
        create = createButton("Connect", buttonPanel);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        // show dialog
        pack();
        setVisible(true);
    }

    /**
     * @return Whether creation of connectors was cancelled
     */
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JComboBox) {
            for (CreateConnectActionBatch batch : actionBatches) {
                for (int i = 0; i < batch.conversionOptions.length; i++) {
                    if (e.getSource() == batch.conversionOptions[i]) {
                        batch.updateParameters(i);
                        return;
                    }
                    if (e.getSource() == batch.streamedType) {
                        SmartConnecting.StreamableType serializedType = (SmartConnecting.StreamableType)batch.streamedType.getSelectedItem();
                        if (i == 0) {
                            batch.conversionOptions[i].setModel(new DefaultComboBoxModel<RemoteConnectOptions>(batch.sourceRuntime.getConversionOptions(batch.sourceType, batch.sourceRuntime.getRemoteType(serializedType.name), false).toArray(new RemoteConnectOptions[0])));
                        } else {
                            batch.conversionOptions[i].setModel(new DefaultComboBoxModel<RemoteConnectOptions>(batch.destinationRuntime.getConversionOptions(batch.destinationRuntime.getRemoteType(serializedType.name), batch.destinationType, false).toArray(new RemoteConnectOptions[0])));
                        }
                        return;
                    }
                }
            }
        } else if (e.getSource() == create) {
            try {
                int flags = RemoteConnectOptions.FINSTRUCTED | (reconnect.isSelected() ? RemoteConnectOptions.RECONNECT : 0) | (optional.isSelected() ? RemoteConnectOptions.OPTIONAL : 0) | (schedulingNeutral.isSelected() ? RemoteConnectOptions.SCHEDULING_NEUTRAL : 0);
                for (CreateConnectActionBatch batch : actionBatches) {
                    RemoteConnectOptions[] options = new RemoteConnectOptions[batch.conversionOptions.length];
                    for (int i = 0; i < options.length; i++) {
                        options[i] = (RemoteConnectOptions)batch.conversionOptions[i].getSelectedItem();
                        options[i].flags = flags;
                        int conversions = options[i].getExplicitConversionCount();
                        for (int j = 0; j < conversions; j++) {
                            RemoteTypeConversion operation = j == 0 ? options[i].operation1 : options[i].operation2;
                            GenericObject parameterValue = null;
                            if (operation.getParameter() != null) {
                                parameterValue = operation.getParameter().getType().getDefaultLocalDataType().createInstanceGeneric(null);
                                StringInputStream stream = new StringInputStream(batch.conversionParameterValues[i][j].getText());
                                parameterValue.deserialize(stream);
                            } else {
                                parameterValue = null;
                            }
                            if (j == 0) {
                                options[i].parameter1 = parameterValue;
                            } else {
                                options[i].parameter2 = parameterValue;
                            }
                        }
                        if (batch.tcpConnector) {
                            try {
                                Duration duration = new Duration();
                                duration.set(Integer.parseInt(minNetworkUpdateInterval.getText()));
                                options[i].setNamedParameter(org.finroc.core.net.generic_protocol.Definitions.URI_CONNECTOR_MINIMAL_UPDATE_INTERVAL, duration.toString());
                            } catch (Exception exception) {
                                // Do not set parameter
                            }
                        }
                    }

                    for (ConnectAction action : batch.actions) {
                        if ((!batch.tcpConnector) && options.length > 0) {
                            action.setConnectOptions(options[0]);
                        } else if (options.length > 1) {
                            action.setConnectOptions(options[0], batch.streamedType.getSelectedItem().toString(), options[1]);
                        } else if (batch.tcpConnector) {
                            throw new RuntimeException("Should have 2 conversion options (programming error)");
                        }
                    }
                }
                cancelled = false;
                close();
            } catch (Exception exception) {
                Finstruct.showErrorMessage("Cannot create connector " + ((actionBatches.size() > 1 || actionBatches.get(0).actions.size() > 1) ? "s" : "") + " like this: " + exception.getMessage(), true, false);
            }
        } else if (e.getSource() == cancel) {
            cancelled = true;
            close();
        }
    }

    /**
     * Batch of create connect actions with same types/direction
     */
    private class CreateConnectActionBatch {

        /** Actions belonging to this batch */
        private final List<ConnectAction> actions = new ArrayList<>();

        /** Whether this a TCP URI connector */
        boolean tcpConnector;

        /** Remote and destination types of all actions in batch */
        private RemoteType sourceType, destinationType;

        /** Remote and destination runtime of all actions in batch */
        private RemoteRuntime sourceRuntime, destinationRuntime;

        /** Combo boxes possibly used */
        private JComboBox<RemoteConnectOptions>[] conversionOptions;
        private JComboBox<SmartConnecting.StreamableType> streamedType;

        /** Parameter labels possibly used */
        private JLabel[][] conversionParameterLabels;

        /** Parameter text fields possibly used */
        private JTextField[][] conversionParameterValues;

        /**
         * Updates parameters elements with specified index depending on current selection of conversionOptions combo box with index
         *
         * @param index Index of combo box
         */
        private void updateParameters(int index) {
            RemoteConnectOptions options = (RemoteConnectOptions)conversionOptions[index].getSelectedItem();
            for (int i = 0; i < 2; i++) {
                RemoteTypeConversion conversion = (i == 0 ? options.operation1 : options.operation2);
                ParameterDefinition parameterDefinition = conversion != null ? conversion.getParameter() : null;
                if (parameterDefinition == null) {
                    conversionParameterLabels[index][i].setText("No parameter");
                    conversionParameterValues[index][i].setText("");
                    conversionParameterValues[index][i].setEnabled(false);
                } else {
                    conversionParameterLabels[index][i].setText(parameterDefinition.getName());
                    GenericObject parameterValue = i == 0 ? options.parameter1 : options.parameter2;
                    if (parameterValue == null) {
                        parameterValue = parameterDefinition.getDefaultValue();
                        if (parameterValue == null) {
                            parameterValue = parameterDefinition.getType().getDefaultLocalDataType().createInstanceGeneric(null);
                        }
                    }
                    conversionParameterValues[index][i].setText(Serialization.serialize(parameterValue));
                    conversionParameterValues[index][i].setEnabled(true);
                }
            }
        }
    }

    /** Actions for connectors to create - and whose options to modify/set */
    private final List<CreateConnectActionBatch> actionBatches = new ArrayList<>();

    /** Check boxes for flags */
    private JCheckBox reconnect, optional, schedulingNeutral;

    /** Minimum number of milliseconds between updates over the network */
    private JTextField minNetworkUpdateInterval;

    /** Buttons at bottom */
    private JButton create, cancel;

    /** Whether creation of connectors was cancelled */
    private boolean cancelled;

    /** UID */
    private static final long serialVersionUID = 8309357653548834926L;


    /**
     * Formats port link to display it dialog
     *
     * @param portLink Full port link
     * @return Formatted port link
     */
    static private String formatPortLink(String sourceLink) {
        String[] parts = sourceLink.split("/");
        if (parts.length == 0) {
            return "Unknown";
        } else if (parts.length == 1) {
            return parts[0];
        } else if (parts.length == 2) {
            return parts[0] + "/" + parts[1];
        }
        return parts[parts.length - 3] + "/" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

}
