/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Alan Harder
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

import hudson.PluginWrapper;
import hudson.model.BuildBadgeAction;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Store promotion level for a build.
 * @author Alan.Harder@sun.com
 */
@ExportedBean(defaultVisibility=2)
public class PromoteAction implements BuildBadgeAction {
    private String level, icon;
    private int levelValue;

    public PromoteAction() { }

    /* Action methods */
    public String getUrlName() { return "promote"; }
    public String getDisplayName() { return ""; }
    public String getIconFileName() { return null; }

    /* Promotion details */
    @Exported public String getLevel() { return level; }
    @Exported public int getLevelValue() { return levelValue; }
    public String getIconPath() {
	if (icon == null) return null;
	// Try plugin images dir, fallback to Hudson images dir
	PluginWrapper wrapper =
	    Hudson.getInstance().getPluginManager().getPlugin(PromotedBuildsSimplePlugin.class);
	return new File(wrapper.baseResourceURL.getPath() + "/images/" + icon).exists()
	    ? "/plugin/" + wrapper.getShortName() + "/images/" + icon
	    : Hudson.RESOURCE_PATH + "/images/16x16/" + icon;
    }

    public List<PromotionLevel> getAllPromotionLevels() {
	return Hudson.getInstance().getPlugin(PromotedBuildsSimplePlugin.class).getLevels();
    }

    /* Save change to promotion level for this build and redirect back to build page */
    public void doIndex(StaplerRequest req, StaplerResponse rsp)
	    throws IOException, ServletException {
	req.findAncestorObject(Job.class).checkPermission(Run.UPDATE);
	levelValue = Integer.parseInt(req.getParameter("level"));
	if (levelValue == 0) {
	    level = icon = null;
	    req.findAncestorObject(Run.class).save();
	} else {
	    PromotionLevel src = getAllPromotionLevels().get(levelValue - 1);
	    level = src.getName();
	    icon = src.getIcon();
	    // Mark as keep-forever when promoting; this also does save()
	    req.findAncestorObject(Run.class).keepLog(true);
	}
	rsp.forwardToPreviousPage(req);
    }
}
