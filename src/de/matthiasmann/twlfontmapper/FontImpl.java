/*
 * Copyright (c) 2008-2012, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.twlfontmapper;

import de.matthiasmann.twl.HAlignment;
import de.matthiasmann.twl.renderer.AnimationState;
import de.matthiasmann.twl.renderer.Font;
import de.matthiasmann.twl.renderer.FontCache;
import de.matthiasmann.twl.renderer.FontParameter;
import de.matthiasmann.twl.utils.StateSelect;
import org.lwjgl.opengl.GL11;

/**
 *
 * @author Matthias Mann
 */
class FontImpl implements Font {
    
    private final TTFFontRenderer font;
    private final StateSelect select;
    private final FontParameter[] fontParams;

    FontImpl(TTFFontRenderer font, StateSelect select, FontParameter ... fontParams) {
        this.font = font;
        this.select = select;
        this.fontParams = fontParams;
    }

    @Override
    public FontCache cacheMultiLineText(FontCache prevCache, CharSequence str, int width, HAlignment align) {
        return null;
    }

    @Override
    public FontCache cacheText(FontCache prevCache, CharSequence str) {
        return null;
    }

    @Override
    public FontCache cacheText(FontCache prevCache, CharSequence str, int start, int end) {
        return null;
    }

    @Override
    public int computeMultiLineTextWidth(CharSequence str) {
        return font.computeMultiLineTextWidth(str);
    }

    @Override
    public int computeTextWidth(CharSequence str) {
        return font.computeTextWidth(str, 0, str.length());
    }

    @Override
    public int computeTextWidth(CharSequence str, int start, int end) {
        return font.computeTextWidth(str, start, end);
    }

    @Override
    public int computeVisibleGlpyhs(CharSequence str, int start, int end, int width) {
        return font.computeVisibleGlpyhs(str, start, end, width);
    }

    @Override
    public int drawMultiLineText(AnimationState as, int x, int y, CharSequence str, int width, HAlignment align) {
        FontParameter fontParam = evalFontParam(as);
        int numLines = 0;
        if(font.prepare(fontParam.get(FontParameter.COLOR))) {
            try {
                numLines = font.drawMultiLineText(x, y, str, width, align);
            } finally {
                font.cleanup();
            }
        }
        return numLines;
    }

    @Override
    public int drawText(AnimationState as, int x, int y, CharSequence str) {
        return drawText(as, x, y, str, 0, str.length());
    }

    @Override
    public int drawText(AnimationState as, int x, int y, CharSequence str, int start, int end) {
        FontParameter fontParam = evalFontParam(as);
        int width = 0;
        if(font.prepare(fontParam.get(FontParameter.COLOR))) {
            try {
                width = font.drawText(x, y, str, start, end);
            } finally {
                font.cleanup();
            }
        }
        drawLine(fontParam, x, y, width);
        return width;
    }

    void drawLine(FontParameter fontParam, int x, int y, int width) {
        if(fontParam.get(FontParameter.UNDERLINE)) {
            drawLine(x, y+font.underlineOffset, x + width);
        }
        if(fontParam.get(FontParameter.LINETHROUGH)) {
            drawLine(x, y+font.lineHeight/2, x + width);
        }
    }
    
    void drawLine(int x0, int y, int x1) {
        int h = font.underlineThickness;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(x0, y);
        GL11.glVertex2i(x1, y);
        GL11.glVertex2i(x1, y+h);
        GL11.glVertex2i(x0, y+h);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @Override
    public int getBaseLine() {
        return font.baseLine;
    }

    @Override
    public int getEM() {
        return font.lineHeight;
    }

    @Override
    public int getEX() {
        return font.ex;
    }

    @Override
    public int getLineHeight() {
        return font.lineHeight;
    }

    @Override
    public int getSpaceWidth() {
        return font.spaceWidth;
    }

    @Override
    public boolean isProportional() {
        return font.proportional;
    }

    @Override
    public void destroy() {
        font.destroy();
    }
    
    FontParameter evalFontParam(AnimationState as) {
        return fontParams[select.evaluate(as)];
    }
}
