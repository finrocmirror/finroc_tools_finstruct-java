package org.finroc.finstruct.views;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.finroc.core.CoreFlags;
import org.finroc.core.FrameworkElement;
import org.finroc.core.FrameworkElementTreeFilter;
import org.finroc.core.datatype.CoreNumber;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortCreationInfo;
import org.finroc.core.port.PortFlags;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.cc.CCInterThreadContainer;
import org.finroc.core.port.cc.CCPortBase;
import org.finroc.core.port.cc.CCPortData;
import org.finroc.core.port.cc.CCPortListener;
import org.finroc.core.port.cc.NumberPort;
import org.finroc.core.port.std.PortBase;
import org.finroc.core.port.std.PortData;
import org.finroc.core.port.std.PortListener;
import org.finroc.finstruct.FinstructView;
import org.finroc.gui.util.gui.MAction;
import org.finroc.gui.util.gui.MActionEvent;
import org.finroc.gui.util.gui.MToolBar;
import org.finroc.log.LogLevel;

/**
 * @author max
 *
 * Displays port-values and lets user manipulate them
 */
public class PortView extends FinstructView implements FrameworkElementTreeFilter.Callback, ActionListener {

    /** UID */
    private static final long serialVersionUID = 7231901570012922905L;

    /** temporary list for rootElementChanged() function */
    private final ArrayList<AbstractPort> tmpResultList = new ArrayList<AbstractPort>();

    /** temporary list for rootElementChanged() function */
    private final ArrayList < PortPanel<? >> panels = new ArrayList < PortPanel<? >> ();

    /** Framework element that all displayed ports are child of */
    private FrameworkElement commonParent;

    /** reference to toolBar */
    private MToolBar toolBar;

    /** Diverse toolbar switches */
    private enum DiverseSwitches { autoUpdate, singleUpdate }

    /** Port description font */
    private static final Font FONT = new JLabel().getFont().deriveFont(Font.PLAIN);

    @Override
    protected synchronized void rootElementChanged() {
        tmpResultList.clear();
        FrameworkElementTreeFilter filter = new FrameworkElementTreeFilter(CoreFlags.STATUS_FLAGS | CoreFlags.IS_PORT, CoreFlags.READY | CoreFlags.PUBLISHED | CoreFlags.IS_PORT);
        filter.traverseElementTree(getRootElement(), this, new StringBuilder());
        setLayout(new BorderLayout());
        showPorts(tmpResultList);
    }

    @Override
    public void treeFilterCallback(FrameworkElement fe) {
        assert(fe instanceof AbstractPort);
        tmpResultList.add((AbstractPort)fe);
    }

    /**
     * Show values of selected ports
     *
     * @param portsToShow port to show
     */
    public void showPorts(List<AbstractPort> portsToShow) {
        super.removeAll();

        // delete panels
        for (PortPanel<?> panel : panels) {
            panel.delete();
        }
        panels.clear();
        if (portsToShow.size() == 0) {
            return;
        }

        // determine common parent
        commonParent = portsToShow.get(0).getParent();
        for (AbstractPort port : portsToShow) {
            while (!port.isChildOf(commonParent)) {
                commonParent = commonParent.getParent();
            }
        }

        // create new panel
        JPanel jp = new JPanel();
        jp.setLayout(new BoxLayout(jp, BoxLayout.PAGE_AXIS));
        JPanel jp2 = new JPanel();
        jp2.setLayout(new BorderLayout());
        add(jp2, BorderLayout.WEST);
        jp2.add(jp, BorderLayout.NORTH);
        for (AbstractPort port : portsToShow) {
            if (!port.getDataType().isMethodType()) {
                PortPanel<?> pp = createPortPanel(port);
                jp.add(pp);
                panels.add(pp);
            }
        }

        revalidate();
        repaint();
    }

    /**
     * Factory method
     *
     * @param port Port to create panel for
     * @return Port panel for port
     */
    private PortPanel<?> createPortPanel(AbstractPort port) {
        if (port.getDataType() == CoreNumber.NUM_TYPE) {
            return new NumericPanel(port);
        } else if (port.getDataType().isCCType()) {
            return new ToStringPanelCC(port);
        } else {
            return new ToStringPanel(port);
        }
    }

    /**
     * @param partner Partner port
     * @return port creation info
     */
    protected static PortCreationInfo createPci(AbstractPort partner) {
        PortCreationInfo pci = new PortCreationInfo(partner.getDescription() + "-panel");
        pci.dataType = partner.getDataType();
        if (partner.isOutputPort()) {
            pci.flags = PortFlags.INPUT_PORT;
        } else {
            pci.flags = PortFlags.ACCEPTS_REVERSE_DATA_PUSH | PortFlags.OUTPUT_PORT;
        }
        return pci;
    }

    @Override
    public void initMenuAndToolBar(JMenuBar menuBar, MToolBar toolBar) {
        this.toolBar = toolBar;
        toolBar.addToggleButton(new MAction(DiverseSwitches.autoUpdate, null, "Auto Update", this), true);
        toolBar.add(new MAction(DiverseSwitches.singleUpdate, null, "Single Update", this));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae instanceof MActionEvent) {
            @SuppressWarnings("rawtypes")
            Enum e = ((MActionEvent)ae).getEnumID();
            if (e == DiverseSwitches.autoUpdate) {
                for (PortPanel<?> panel : panels) {
                    panel.setAutoUpdate(toolBar.isSelected(DiverseSwitches.autoUpdate));
                }
            } else if (e == DiverseSwitches.singleUpdate && (!toolBar.isSelected(DiverseSwitches.autoUpdate))) {
                for (PortPanel<?> panel : panels) {
                    panel.initialValueRetrieve = true;
                    panel.setAutoUpdate(true);
                }
            }
        }
    }


    /**
     * Panel displaying port description and value
     */
    public abstract class PortPanel<P extends AbstractPort> extends JPanel {

        /** UID */
        private static final long serialVersionUID = 5075733994664703804L;

        /** Partner (network) port */
        protected final AbstractPort partner;

        /** port to get and set data */
        protected final P port;

        /** initially true - until first value is retrieved from server */
        protected boolean initialValueRetrieve = true;

        /**
         * @param partner Partner (network) port
         * @param port port to get and set data
         */
        protected PortPanel(AbstractPort partner, P port) {
            this.partner = partner;
            this.port = port;
            setLayout(new BorderLayout());
            JLabel jl = new JLabel(partner.getQualifiedLink().substring(commonParent.getQualifiedLink().length() + 1));
            jl.setFont(FONT);
            add(jl, BorderLayout.WEST);
        }

        /** delete panel */
        public void delete() {
            port.managedDelete();
        }

        /** Init port */
        protected void init() {
            port.init();
            if (port.isOutputPort()) {
                port.connectToTarget(partner);
            } else {
                port.connectToSource(partner);
            }
            //setAutoUpdate(false);
        }

        /**
         * Sets auto-update for panel
         *
         * @param b value to set
         */
        public void setAutoUpdate(boolean b) {
            if (port.isOutputPort()) {
                port.setReversePushStrategy(b);
            } else {
                port.setPushStrategy(b);
            }
        }

        /**
         * Does some processing when port change is received
         *
         * @return Update value in text field (or whatever)
         */
        public boolean doPortChange() {
            boolean aa = toolBar.isSelected(DiverseSwitches.autoUpdate);
            if (initialValueRetrieve) {
                initialValueRetrieve = false;
                if (!aa) {
                    setAutoUpdate(false);
                }
                return true;
            }
            return aa;
        }
    }

    /**
     * Numeric panel
     */
    public class NumericPanel extends PortPanel<NumberPort> implements ActionListener, CCPortListener<CoreNumber> {

        /** UID */
        private static final long serialVersionUID = 6278815802759699130L;

        /** text field */
        private JTextField text = new JTextField();

        protected NumericPanel(AbstractPort partner) {
            super(partner, new NumberPort(createPci(partner)));
            port.addPortListener(this);
            JPanel jp = new JPanel();
            jp.setLayout(new BorderLayout());
            super.add(jp, BorderLayout.EAST);
            JButton jb = new JButton("Apply");
            jb.addActionListener(this);
            jp.add(jb, BorderLayout.EAST);
            jp.add(text, BorderLayout.CENTER);
            text.setMinimumSize(new Dimension(100, text.getMinimumSize().height));
            text.setPreferredSize(text.getMinimumSize());
            text.setMaximumSize(text.getMinimumSize());
            text.setHorizontalAlignment(JTextField.RIGHT);
            super.init();
        }

        @Override
        public void portChanged(CCPortBase origin, CoreNumber value) {
            if (super.doPortChange()) {
                text.setText(value.toString());
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {

            String s = text.getText();
            boolean dbl = s.contains(",") || s.contains(".") || s.contains("e") || s.contains("E");
            double d = 0.0;
            long l = 0;
            try {
                if (dbl) {
                    d = Double.parseDouble(s);
                } else {
                    l = Long.parseLong(s);
                }
            } catch (Exception e2) {
                logDomain.log(LogLevel.LL_ERROR, "PortView", "Cannot parse: " + s);
                return;
            }

            // apply button
            if (partner.isReady()) {
                @SuppressWarnings("unchecked")
                CCInterThreadContainer<CoreNumber> cic = (CCInterThreadContainer<CoreNumber>)ThreadLocalCache.get().getUnusedInterThreadBuffer(CoreNumber.NUM_TYPE);
                if (dbl) {
                    cic.getData().setValue(d);
                } else {
                    cic.getData().setValue(l);
                }
                partner.asNetPort().getAdminInterface().setRemotePortValue(partner.asNetPort(), cic);
            }
        }
    }

    /**
     * Displays toString value of PortData
     */
    @SuppressWarnings("rawtypes")
    public class ToStringPanel extends PortPanel<PortBase> implements PortListener {

        /** UID */
        private static final long serialVersionUID = -5180323460085887154L;

        /** Label displaying value */
        protected JLabel label = new JLabel();

        protected ToStringPanel(AbstractPort partner) {
            super(partner, new PortBase(createPci(partner)));
            add(label, BorderLayout.EAST);
            port.addPortListenerRaw(this);
            init();
        }

        @Override
        public void portChanged(PortBase origin, PortData value) {
            if (super.doPortChange()) {
                label.setText(value.toString());
            }
        }
    }

    /**
     * Displays toString value of PortData
     */
    @SuppressWarnings("rawtypes")
    public class ToStringPanelCC extends PortPanel<CCPortBase> implements CCPortListener {

        /** UID */
        private static final long serialVersionUID = -5180323460085887154L;

        /** Label displaying value */
        protected JLabel label = new JLabel();

        protected ToStringPanelCC(AbstractPort partner) {
            super(partner, new CCPortBase(createPci(partner)));
            add(label, BorderLayout.EAST);
            port.addPortListenerRaw(this);
            init();
        }

        @Override
        public void portChanged(CCPortBase origin, CCPortData value) {
            if (super.doPortChange()) {
                label.setText(value.toString());
            }
        }
    }
}

