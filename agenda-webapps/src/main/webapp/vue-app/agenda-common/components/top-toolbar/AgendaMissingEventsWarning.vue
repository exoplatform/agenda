<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->

<template>
  <!--
    An account that could not be read leaves the period lighter than the real
    one. The calendar is a view, not a decision: it says so with a small sign
    beside the period it concerns, instead of a line pushing the grid down,
    and keeps the sentence naming the accounts for whoever stops on it.
  -->
  <v-menu
    v-model="menu"
    :close-on-content-click="false"
    max-width="320"
    offset-y
    bottom>
    <template #activator="{ on: menuOn, attrs: menuAttrs }">
      <v-tooltip v-model="tooltip" bottom>
        <template #activator="{ on: tooltipOn, attrs: tooltipAttrs }">
          <v-btn
            :aria-label="message"
            class="agenda-missing-events-warning my-auto ms-1"
            icon
            small
            v-bind="{...menuAttrs, ...tooltipAttrs}"
            v-on="{...menuOn, ...tooltipOn}">
            <v-icon size="16" class="warning--text">
              fa-exclamation-triangle
            </v-icon>
          </v-btn>
        </template>
        <span>{{ message }}</span>
      </v-tooltip>
    </template>
    <v-card class="pa-3" flat>
      <div class="d-flex text-body-2 warning--text">
        <v-icon size="14" class="me-2 mt-1 warning--text align-self-start">
          fa-exclamation-triangle
        </v-icon>
        <span>{{ message }}</span>
      </div>
      <div class="d-flex justify-end mt-2">
        <v-btn
          class="btn"
          small
          @click="retry">
          {{ $t('agenda.retry') }}
        </v-btn>
      </div>
    </v-card>
  </v-menu>
</template>
<script>
export default {
  props: {
    /**
     * The sentence naming the accounts that could not be read, as the
     * application states it: shown as the tooltip, the popover body and the
     * accessible name of the icon.
     */
    message: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    menu: false,
    tooltip: false,
  }),
  watch: {
    /**
     * Hides the tooltip once the popover opens: both say the same sentence,
     * and the tooltip would otherwise sit over the popover while the pointer
     * stays on the icon.
     *
     * @returns {void}
     */
    menu() {
      if (this.menu) {
        this.tooltip = false;
      }
    },
  },
  methods: {
    /**
     * Asks the application to read the remote accounts of the displayed period
     * again, and closes the popover: the icon stays or goes with the outcome.
     *
     * @returns {void}
     */
    retry() {
      this.menu = false;
      this.$root.$emit('agenda-remote-events-retry');
    },
  },
};
</script>
