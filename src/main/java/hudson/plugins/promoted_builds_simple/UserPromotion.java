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

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

public class UserPromotion extends UserProperty implements Action {

    private List<PromoteCause> promotions = null;

    @DataBoundConstructor
    @SuppressWarnings("LeakingThisInConstructor")
    public UserPromotion(User u) {
        this.user = u;
        this.promotions = new ArrayList<PromoteCause>();
    }

    public final String getRootPath() {
        return Stapler.getCurrentRequest().getRootPath();
    }

    public List<PromoteCause> getPromotions() {
        return this.promotions;
    }

    public void addPromotion(PromoteCause promotion) throws IOException {
      if (this.promotions == null)
      {
        this.promotions = new ArrayList<PromoteCause>();
      }
      promotions.add(promotion);
      user.save();
    }

    public void removePromotion(PromoteCause promotion) throws IOException {
        if (this.promotions != null) {
            promotions.remove(promotion);
        }
        user.save();
    }

    public User getUser() {
        return user;
    }

    public String getIconFileName() {
        return "star-gold.gif";
    }

    public String getDisplayName() {
        return "Build Promotions";
    }

    public String getUrlName() {
        return "promotions";
    }

    @Extension
    public static final class DescriptorImpl extends UserPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "";
        }

        @Override
        public UserProperty newInstance(User user) {
            UserPromotion pc = new UserPromotion(user);
            return pc;
        }
    }

    @Extension
    public static final class ListenerImpl extends RunListener<Run> {

        @Override
        public void onDeleted(Run r) {
            PromoteAction pro = (PromoteAction) r.getAction(PromoteAction.class);
            if (pro != null && pro.causes != null) {
                for (PromoteCause cause : pro.causes) {
                    User user = Hudson.getInstance().getUser(cause.getUserName());
                    if (user != null) {
                        UserPromotion promotion = user.getProperty(UserPromotion.class);
                        if (promotion != null) {
                            try {
                                promotion.removePromotion(cause);
                            } catch (IOException ex) {
                                Logger.getLogger(UserPromotion.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
            }
        }
    }
}
