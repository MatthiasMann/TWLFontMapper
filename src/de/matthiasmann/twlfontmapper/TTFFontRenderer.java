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

import de.matthiasmann.javafreetype.FreeTypeFont;
import de.matthiasmann.javafreetype.FreeTypeGlyphInfo;
import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.HAlignment;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLRenderer;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLTexture;
import de.matthiasmann.twl.renderer.lwjgl.VertexArray;
import de.matthiasmann.twl.utils.TextUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.opengl.GL11;

/**
 *
 * @author Matthias Mann
 */
public class TTFFontRenderer {
    
    private static final int BATCH_SIZE = 512;
    
    private static final int LOG2_PAGE_SIZE = 9;
    private static final int PAGE_SIZE = 1 << LOG2_PAGE_SIZE;
    private static final int PAGES = (Character.MAX_CODE_POINT+1) / PAGE_SIZE;
    
    private final int numKerningPages;
    private final Glyph[] glyphs;
    private final Glyph[][] unicode2glyphs;
    
    private final FontData fontData;
    private final FreeTypeFont font;
    private final FreeTypeFont.Size size;
    private final ArrayList<Row> rows;
    private final ByteBuffer tmpBuf;
    private final VertexArray vertexArray;
    private final FloatBuffer vaBuffer;
    private int y;

    protected LWJGLTexture texture;
    protected float texWidthScale;
    protected float texHeightScale;
    
    protected final int lineHeight;
    protected final int baseLine;
    protected final int underlineOffset;
    protected final int underlineThickness;
    protected final int spaceWidth;
    protected final int ex;
    protected boolean proportional;

    public TTFFontRenderer(LWJGLRenderer renderer, FontData fontData, FreeTypeFont.Size size) throws IOException {
        this.fontData = fontData;
        this.font = fontData.getFont();
        this.size = size;
        this.rows = new ArrayList<Row>();
        this.glyphs = new Glyph[font.getNumGlyphs() + 1];
        this.unicode2glyphs = new Glyph[PAGES][];
        this.numKerningPages = (font.getNumGlyphs()+PAGE_SIZE) / PAGE_SIZE;
        
        assert font.getActiveSize() == size;
        
        // use NEAREST to prevent reading neighbour texels (there is no gap and possible garbage around glyphs)
        texture = new LWJGLTexture(renderer, 1024, 1024, null, LWJGLTexture.Format.ALPHA, LWJGLTexture.Filter.NEAREST);
        this.texWidthScale = 1.0f / texture.getTexWidth();
        this.texHeightScale = 1.0f / texture.getTexHeight();
        
        lineHeight = font.getLineHeight();
        baseLine = font.getAscent();
        underlineThickness = font.getUnderlineThickness();
        underlineOffset = baseLine - font.getUnderlinePosition() - underlineThickness;
        
        int maxSize = 4 * font.getMaxWidth() * (font.getMaxDescent() + baseLine);
        
        tmpBuf = ByteBuffer.allocateDirect(maxSize);
        vertexArray = new VertexArray();
        vaBuffer = vertexArray.allocate(BATCH_SIZE);
        
        Glyph g = getGlyph(' ');
        spaceWidth = (g != null) ? g.xadvance + g.width : 1;
        
        Glyph gx = getGlyph('x');
        ex = (gx != null) ? gx.height : 1;
    }
    
    public void destroy() {
        if(texture != null) {
            texture.destroy();
            texture = null;
        }
    }
    
    final void setRawKerning(Glyph g, int toGlyph) throws IOException {
        int value = font.getKerning(g.glyphIndex, toGlyph).x;
        if(value != 0) {
            if(g.kerning == null) {
                g.kerning = new byte[numKerningPages][];
            }
            int pageIdx = toGlyph >>> LOG2_PAGE_SIZE;
            byte[] page = g.kerning[pageIdx];
            if(page == null) {
                g.kerning[pageIdx] = page = new byte[PAGE_SIZE];
            }
            page[toGlyph & (PAGE_SIZE-1)] = (byte)value;
        }
    }
    
    final Glyph getGlyph(int codePoint) {
        int pageIdx = codePoint >> LOG2_PAGE_SIZE;
        if(pageIdx < unicode2glyphs.length) {
            Glyph[] page = unicode2glyphs[pageIdx];
            if(page != null) {
                Glyph g = page[codePoint & (PAGE_SIZE-1)];
                if(g != null) {
                    return g;
                }
            }
        }
        return makeGlyphFromCodepoint(codePoint);
    }
    
    private Glyph makeGlyphFromCodepoint(int codepoint) {
        Glyph g = null;
        try {
            int glyphIndex = font.getGlyphForCodePoint(codepoint);
            g = makeGlyph(glyphIndex);
            if(g == null) {
                g = makeGlyph(0);
            }
        } catch (IOException e) {
            Logger.getLogger(TTFFontRenderer.class.getName()).log(Level.SEVERE, null, e);
        }
        
        if(g == null) {
            g = new Glyph(0, 0, 0, 0, 0, 0);
        }
        
        int pageIdx = codepoint >> LOG2_PAGE_SIZE;
        Glyph[] page = unicode2glyphs[pageIdx];
        if(page == null) {
            unicode2glyphs[pageIdx] = page = new Glyph[PAGE_SIZE];
        }
        page[codepoint & (PAGE_SIZE - 1)] = g;
        
        return g;
    }
    
    private Glyph makeGlyph(int glyphIndex) throws IOException {
        Glyph g = glyphs[glyphIndex];
        if(g == null) {
            font.setActiveSize(size);
            FreeTypeGlyphInfo glyphInfo = font.loadGlyph(glyphIndex);
            if(glyphInfo.getWidth() <= texture.getTexWidth() &&
                    glyphInfo.getHeight() <= texture.getTexHeight()) {
                g = new Glyph(glyphIndex,
                        glyphInfo.getWidth(),
                        glyphInfo.getHeight(),
                        glyphInfo.getOffsetX(),
                        baseLine - glyphInfo.getOffsetY(),
                        glyphInfo.getAdvanceX());
                glyphs[glyphIndex] = g;
                
                if(font.hasKerning()) {
                    fontData.readRawKerning(this, g);
                }
            }
        }
        return g;
    }

    final Glyph getGlyphRender(int codePoint) {
        final Glyph g = getGlyph(codePoint);
        if(g != null && g.tx0 < 0) {
            uploadGlyph(g);
        }
        return g;
    }

    private void uploadGlyph(Glyph g) {
        Row row = findRow(g.width, g.height);

        try {
            font.setActiveSize(size);
            font.loadGlyph(g.glyphIndex);

            tmpBuf.clear();
            if(font.copyGlyphToByteBuffer(tmpBuf, g.width)) {
                tmpBuf.flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, row.x, row.y, g.width, g.height, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, tmpBuf);
                g.set(row.x, row.y, texWidthScale, texHeightScale);
                row.x += g.width;
            }
        } catch (IOException e) {
            Logger.getLogger(TTFFontRenderer.class.getName()).log(Level.SEVERE, null, e);
        }
    }
    
    private Row findRow(int width, int height) {
        // align row height to reduce the number of similar sized rows
        height = (height + 3) & -4;
        
        int end = texture.getTexWidth() - width;
        Row bestRow = null;
        for(int i=0 ; i<rows.size() ; i++) {
            Row row = rows.get(i);
            if(row.height >= height && row.height < height*2 && row.x <= end) {
                if(bestRow == null || row.height < bestRow.height) {
                    bestRow = row;
                }
            }
        }
        if(bestRow != null) {
            return bestRow;
        }
        return newRow(height);
    }
    
    private Row newRow(int height) {
        if(texture.getTexHeight() - y < height) {
            flushTexture();
        }
        
        System.out.println("made row " + height);
        Row row = new Row(y, height);
        y += height;
        rows.add(row);
        return row;
    }
    
    private void flushTexture() {
        flush();
        rows.clear();
        y = 0;
        for(Glyph g : glyphs) {
            if(g != null && g.width > 0 && g.height > 0) {
                g.tx0 = -1f;
            }
        }
    }

    public boolean prepare(Color color) {
        if(texture.bind(color)) {
            vertexArray.bind();
            return true;
        }
        return false;
    }

    public void cleanup() {
        flush();
        vertexArray.unbind();
    }
    
    private void flush() {
        int pos = vaBuffer.position();
        if(pos > 0) {
            vertexArray.drawVertices(0, pos >> 2);
            vaBuffer.clear();
        }
    }
    
    public int computeTextWidth(CharSequence str, int start, int end) {
        int width = 0;
        Glyph lastGlyph = null;
        while(start < end) {
            int ch = str.charAt(start++);
            lastGlyph = getGlyph(ch);
            if(lastGlyph != null) {
                width = lastGlyph.xadvance;
                break;
            }
        }
        while(start < end) {
            char ch = str.charAt(start++);
            Glyph g = getGlyph(ch);
            if(g != null) {
                width += lastGlyph.getKerning(g.glyphIndex);
                lastGlyph = g;
                width += g.xadvance;
            }
        }
        return width;
    }

    public int computeVisibleGlpyhs(CharSequence str, int start, int end, int availWidth) {
        int index = start;
        int width = 0;
        Glyph lastGlyph = null;
        for(; index < end ; index++) {
            char ch = str.charAt(index);
            Glyph g = getGlyph(ch);
            if(g != null) {
                if(lastGlyph != null) {
                    width += lastGlyph.getKerning(g.glyphIndex);
                }
                lastGlyph = g;
                if(proportional) {
                    width += g.xadvance;
                    if(width > availWidth) {
                        break;
                    }
                } else {
                    if(width + g.width + g.xoffset > availWidth) {
                        break;
                    }
                    width += g.xadvance;
                }
            }
        }
        return index - start;
    }
    
    public void computeMultiLineInfo(CharSequence str, int width, HAlignment align, int[] multiLineInfo) {
        int start = 0;
        int idx = 0;
        while(start < str.length()) {
            int lineEnd = TextUtil.indexOf(str, '\n', start);
            int lineWidth = computeTextWidth(str, start, lineEnd);
            int xoff = width - lineWidth;
            if(align == HAlignment.LEFT) {
                xoff = 0;
            } else if(align == HAlignment.CENTER) {
                xoff /= 2;
            }
            multiLineInfo[idx++] = (lineWidth << 16) | (xoff & 0xFFFF);
            start = lineEnd + 1;
        }
    }
    
    public int computeMultiLineTextWidth(CharSequence str) {
        int start = 0;
        int width = 0;
        while(start < str.length()) {
            int lineEnd = TextUtil.indexOf(str, '\n', start);
            int lineWidth = computeTextWidth(str, start, lineEnd);
            width = Math.max(width, lineWidth);
            start = lineEnd + 1;
        }
        return width;
    }

    public int drawText(int x, int y, CharSequence str, int start, int end) {
        FloatBuffer va = vaBuffer;
        int startX = x;
        Glyph lastGlyph = null;
        while(start < end) {
            lastGlyph = getGlyphRender(str.charAt(start++));
            if(lastGlyph != null) {
                if(lastGlyph.width > 0) {
                    lastGlyph.draw(va, x, y);
                }
                x += lastGlyph.xadvance;
                break;
            }
        }
        while(start < end) {
            char ch = str.charAt(start++);
            Glyph g = getGlyphRender(ch);
            if(g != null) {
                x += lastGlyph.getKerning(g.glyphIndex);
                lastGlyph = g;
                if(g.width > 0) {
                    if(!va.hasRemaining()) {
                        flush();
                    }
                    g.draw(va, x, y);
                }
                x += g.xadvance;
            }
        }
        return x - startX;
    }
    
    public int drawMultiLineText(int x, int y, CharSequence str, int width, HAlignment align) {
        int start = 0;
        int numLines = 0;
        while(start < str.length()) {
            int lineEnd = TextUtil.indexOf(str, '\n', start);
            int xoff = 0;
            if(align != HAlignment.LEFT) {
                int lineWidth = computeTextWidth(str, start, lineEnd);
                xoff = width - lineWidth;
                if(align == HAlignment.CENTER) {
                    xoff /= 2;
                }
            }
            drawText(x + xoff, y, str, start, lineEnd);
            start = lineEnd + 1;
            y += lineHeight;
            numLines++;
        }
        return numLines;
    }
    
    static class Row {
        final int y;
        final int height;
        int x;

        Row(int y, int height) {
            this.y = y;
            this.height = height;
        }
    }
    
    static class Glyph {
        final int glyphIndex;
        final short width;
        final short height;
        final short xoffset;
        final short yoffset;
        final short xadvance;
        float tx0;
        float ty0;
        float tx1;
        float ty1;
        private byte[][] kerning;

        Glyph(int glyphIndex, int width, int height, int xoffset, int yoffset, int xadvance) {
            this.glyphIndex = glyphIndex;
            this.width = (short)width;
            this.height = (short)height;
            this.xoffset = (short)xoffset;
            this.yoffset = (short)yoffset;
            this.xadvance = (short)xadvance;
            
            if(width > 0 && height > 0) {
                // mark glyph as not in texture
                tx0 = -1f;
            }
        }
        
        void set(int x, int y, float texWidthScale, float texHeightScale) {
            int w = width;
            int h = height;
            float fx = x;
            float fy = y;
            if(w == 1) {
                fx += 0.5f;
                w = 0;
            }
            if(h == 1) {
                fy += 0.5f;
                h = 0;
            }
            tx0 = fx * texWidthScale;
            ty0 = fy * texHeightScale;
            tx1 = (fx + w) * texWidthScale;
            ty1 = (fy + h) * texHeightScale;
        }
        
        final void draw(FloatBuffer va, int x, int y) {
            final int w = width;
            final int h = height;
            x += xoffset;
            y += yoffset;
            va.put(tx0).put(ty0).put(x    ).put(y)
              .put(tx0).put(ty1).put(x    ).put(y + h)
              .put(tx1).put(ty1).put(x + w).put(y + h)
              .put(tx1).put(ty0).put(x + w).put(y);
        }
        
        final int getKerning(int glyphIdx) {
            if(kerning != null) {
                byte[] page = kerning[glyphIdx >>> LOG2_PAGE_SIZE];
                if(page != null) {
                    return page[glyphIdx & (PAGE_SIZE-1)];
                }
            }
            return 0;
        }
    }
    
}
