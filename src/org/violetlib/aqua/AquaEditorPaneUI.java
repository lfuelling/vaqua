/*
 * Copyright (c) 2018 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.violetlib.aqua;

import java.awt.*;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicEditorPaneUI;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.violetlib.jnr.Insets2D;
import org.violetlib.jnr.aqua.AquaUIPainter;

public class AquaEditorPaneUI extends BasicEditorPaneUI implements FocusRingOutlineProvider, AquaComponentUI {

    public static ComponentUI createUI(JComponent c){
        return new AquaEditorPaneUI();
    }

    private JTextComponent editor;
    private boolean oldDragState = false;
    private FocusListener focusListener;
    protected PropertyChangeListener propertyChangeListener;
    protected @NotNull BasicContextualColors colors;
    protected @Nullable AppearanceContext appearanceContext;

    public AquaEditorPaneUI() {
        colors = AquaColors.TEXT_COLORS;
    }

    @Override
    public void installUI(JComponent c) {
        if (c instanceof JTextComponent) {
            editor = (JTextComponent) c;
            super.installUI(c);
        }
    }

    @Override
    public void uninstallUI(JComponent c) {
        super.uninstallUI(c);
        editor = null;
    }

    @Override
    protected void installDefaults(){
        if(!GraphicsEnvironment.isHeadless()){
            oldDragState = editor.getDragEnabled();
            editor.setDragEnabled(true);
        }
        super.installDefaults();
        LookAndFeel.installProperty(editor, "opaque", false);
        configureAppearanceContext(null);
    }

    @Override
    protected void uninstallDefaults(){
        if(!GraphicsEnvironment.isHeadless()){
            editor.setDragEnabled(oldDragState);
        }
        super.uninstallDefaults();
    }

    @Override
    protected void installListeners(){
        super.installListeners();
        focusListener = createFocusListener();
        editor.addFocusListener(focusListener);
        propertyChangeListener = new AquaPropertyChangeHandler();
        editor.addPropertyChangeListener(propertyChangeListener);
        AppearanceManager.installListener(editor);
    }

    @Override
    protected void uninstallListeners(){
        AppearanceManager.uninstallListener(editor);
        editor.removeFocusListener(focusListener);
        focusListener = null;
        super.uninstallListeners();
    }

    @Override
    protected void installKeyboardActions() {
        super.installKeyboardActions();
        AquaKeyBindings bindings = AquaKeyBindings.instance();
        bindings.setDefaultAction(getKeymapName());
        bindings.installAquaUpDownActions(editor);
    }

    protected FocusListener createFocusListener(){
        return new AquaFocusHandler();
    }

    protected class AquaPropertyChangeHandler implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent e) {
            String prop = e.getPropertyName();
            if ("enabled".equals(prop)) {
                configureAppearanceContext(null);
            }
        }
    }

    @Override
    public void appearanceChanged(@NotNull JComponent c, @NotNull AquaAppearance appearance) {
        configureAppearanceContext(appearance);
    }

    @Override
    public void activeStateChanged(@NotNull JComponent c, boolean isActive) {
        configureAppearanceContext(null);
    }

    protected void configureAppearanceContext(@Nullable AquaAppearance appearance) {
        if (appearance == null) {
            appearance = AppearanceManager.ensureAppearance(editor);
        }
        AquaUIPainter.State state = getState();
        appearanceContext = new AppearanceContext(appearance, state, false, false);
        AquaColors.installColors(editor, appearanceContext, colors);
        editor.repaint();
    }

    protected AquaUIPainter.State getState() {
        if (editor.isEnabled()) {
            boolean isActive = AquaFocusHandler.isActive(editor);
            boolean hasFocus = AquaFocusHandler.hasFocus(editor);
            return isActive
                    ? (hasFocus ? AquaUIPainter.State.ACTIVE_DEFAULT : AquaUIPainter.State.ACTIVE)
                    : AquaUIPainter.State.INACTIVE;
        } else {
            return AquaUIPainter.State.DISABLED;
        }
    }

    @Override
    public void update(Graphics g, JComponent c) {
        AquaAppearance appearance = AppearanceManager.registerCurrentAppearance(c);
        super.update(g, c);
        AppearanceManager.restoreCurrentAppearance(appearance);
    }

    protected void paintSafely(Graphics g) {

        Color background = getBackground();
        paintBackgroundSafely(g, background);

        // If the painter has specified a non-integer top inset, attempt to support that by translation.
        // This works with AquaTextComponentBorder, which will have rounded down the top inset.

        if (g instanceof Graphics2D) {
            Border b = editor.getBorder();
            if (b instanceof Border2D) {
                Border2D ab = (Border2D) b;
                Insets2D n = ab.getBorderInsets2D(editor);
                if (n != null) {
                    float top = n.getTop();
                    float floor = (float) Math.floor(top);
                    if (top - floor > 0.001f) {
                        Graphics2D gg = (Graphics2D) g.create();
                        gg.translate(0, top - floor);
                        super.paintSafely(gg);
                        gg.dispose();
                        return;
                    }
                }
            }
        }

        super.paintSafely(g);
    }

    protected @Nullable Color getBackground() {
        return editor.getBackground();
    }

    protected void paintBackgroundSafely(@NotNull Graphics g, @Nullable Color background) {
        Border b = editor.getBorder();
        if (b instanceof AquaTextComponentBorder) {
            AquaTextComponentBorder tb = (AquaTextComponentBorder) b;
            tb.paintBackground(editor, g, background);
        }
    }

    protected void paintBackground(Graphics g) {
        // we have already ensured that the background is painted to our liking
        // by paintBackgroundSafely(), called from paintSafely().
    }

    @Override
    protected Caret createCaret() {
        return new AquaCaret();
    }

    @Override
    public Shape getFocusRingOutline(JComponent c) {
        Border b = c.getBorder();
        if (b instanceof FocusRingOutlineProvider) {
            FocusRingOutlineProvider p = (FocusRingOutlineProvider) b;
            return p.getFocusRingOutline(c);
        }
        return new Rectangle(0, 0, c.getWidth(), c.getHeight());
    }
}
