/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.promoted_builds_simple;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.SimpleBuildSelectorDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Build selector for Copy Artifacts plugin to copy from latest build
 * of a particular promotion level (or higher).
 * @author Alan.Harder@sun.com
 */
public class PromotedBuildSelector extends BuildSelector {
    private int level;

    @DataBoundConstructor
    public PromotedBuildSelector(int level) {
        this.level = level;
    }

    @Override
    public boolean isSelectable(Run<?,?> run) {
        PromoteAction pa = run.getAction(PromoteAction.class);
        return (pa != null && pa.getLevelValue() >= level);
    }

    @Extension
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                PromotedBuildSelector.class, Messages._PromotedBuildSelector_DisplayName());
}
