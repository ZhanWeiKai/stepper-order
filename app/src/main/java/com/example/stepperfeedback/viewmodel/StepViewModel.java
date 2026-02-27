/*
Copyright 2016 StepStone Services

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.example.stepperfeedback.viewmodel;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Contains information about a step.
 */
public class StepViewModel {

    @Nullable
    private final CharSequence title;

    @Nullable
    private final CharSequence subtitle;

    private StepViewModel(@Nullable CharSequence title, @Nullable CharSequence subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    @Nullable
    public CharSequence getTitle() {
        return title;
    }

    @Nullable
    public CharSequence getSubtitle() {
        return subtitle;
    }

    /**
     * Builder for {@link StepViewModel}.
     */
    public static class Builder {

        private final Context context;

        @Nullable
        private CharSequence title;

        @Nullable
        private CharSequence subtitle;

        public Builder(@NonNull Context context) {
            this.context = context;
        }

        /**
         * Sets the title for the step tab.
         *
         * @param title string resource for the title
         * @return this
         */
        public Builder setTitle(@StringRes int title) {
            this.title = context.getString(title);
            return this;
        }

        /**
         * Sets the title for the step tab.
         *
         * @param title title string
         * @return this
         */
        public Builder setTitle(@Nullable CharSequence title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the subtitle for the step tab.
         *
         * @param subtitle string resource for the subtitle
         * @return this
         */
        public Builder setSubtitle(@StringRes int subtitle) {
            this.subtitle = context.getString(subtitle);
            return this;
        }

        /**
         * Sets the subtitle for the step tab.
         *
         * @param subtitle subtitle string
         * @return this
         */
        public Builder setSubtitle(@Nullable CharSequence subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public StepViewModel create() {
            return new StepViewModel(title, subtitle);
        }
    }
}
