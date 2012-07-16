/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
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
import hudson.model.AbstractProject;
import hudson.model.BuildBadgeAction;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.User;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Store promotion level for a build.
 * @author Alan Harder
 */
@ExportedBean(defaultVisibility = 2)
public class PromoteAction implements BuildBadgeAction {

    private String level, icon;
    private int levelValue;
    public List<PromoteCause> causes = new ArrayList<PromoteCause>();

    public PromoteAction() {
    }

    /* Action methods */
    public String getUrlName() {
        return "promote";
    }

    public String getDisplayName() {
        return "";
    }

    public String getIconFileName() {
        return null;
    }

    /* Promotion details */
    @Exported
    public String getLevel() {
        return level;
    }

    @Exported
    public int getLevelValue() {
        return levelValue;
    }

    public String getIconPath() {
        if (icon == null || icon.startsWith("/")) {
            return icon;
        }
        // Try plugin images dir, fallback to main images dir
        PluginWrapper wrapper =
                Hudson.getInstance().getPluginManager().getPlugin(PromotedBuildsSimplePlugin.class);
        return new File(wrapper.baseResourceURL.getPath() + "/images/" + icon).exists()
                ? "/plugin/" + wrapper.getShortName() + "/images/" + icon
                : Hudson.RESOURCE_PATH + "/images/16x16/" + icon;
    }

    public static List<PromotionLevel> getAllPromotionLevels() {
        return Hudson.getInstance().getPlugin(PromotedBuildsSimplePlugin.class).getLevels();
    }

    /* Save change to promotion level for this build and redirect back to build page */
    public void doIndex(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {

        Job j = req.findAncestorObject(Job.class);
        j.checkPermission(Run.UPDATE);

        if (this.causes == null) {
            this.causes = new ArrayList<PromoteCause>();
        }

        Run run = req.findAncestorObject(Run.class);
        levelValue = Integer.parseInt(req.getParameter("level"));
        PromotionLevel src = null;
        if (levelValue == 0) {
            level = icon = null;
            run.save();
        } else {
            src = getAllPromotionLevels().get(levelValue - 1);
            level = src.getName();
            icon = src.getIcon();

            Housekeeper hk = (Housekeeper) j.getProperty(Housekeeper.class);
            if (hk != null) {
                hk.clean(req, src);
            }
        }

        boolean skip = false;
        String user = Hudson.getAuthentication().getName();

        PromotedPermalinkProjectAction permalinkAction = (PromotedPermalinkProjectAction) j.getAction(PromotedPermalinkProjectAction.class);
        if (permalinkAction == null) {
            permalinkAction = new PromotedPermalinkProjectAction((AbstractProject) j);
        }
        permalinkAction.registerPromotion(level, run.number);


        if (!this.causes.isEmpty()) {
            PromoteCause last = this.causes.get(this.causes.size() - 1);
            if ((last.levelName == null && level == null)
                    || (last.levelName != null && last.levelName.equals(level) && last.getUserName().equals(user))) {
                // double-tap? submit during Jenkins startup?
                skip = true;
            }
        }

        if (!skip) {
            PromoteCause pc = new PromoteCause(user, run, levelValue, level);
            this.causes.add(pc);
            User u = Hudson.getInstance().getUser(user);
            if (u != null) {
                UserPromotion up = u.getProperty(UserPromotion.class);
                if (up == null) {
                    up = new UserPromotion(u);
                }
                up.addPromotion(pc);
            }
        }

        // Mark as keep-forever when promoting; this also does save()
        if (src != null && src.isAutoKeep()) {
            run.keepLog(true);
        } else {
            run.save();
        }

        rsp.forwardToPreviousPage(req);
    }
}
