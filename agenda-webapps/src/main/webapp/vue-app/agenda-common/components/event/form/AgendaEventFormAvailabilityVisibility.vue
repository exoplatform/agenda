/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see gnu.org/licenses.
 */
<!--
  One row, two selects, as Google Calendar puts them (EXO-90327 + EXO-90322):
  what this event does to the time on the left, who may read it on the right.
  They are separate features with separate back ends, and one row because a
  lone "Busy / Free" select reads as missing its neighbour.

  Neither value is written until the user picks one. An event created before
  either control existed carries DEFAULT availability, which means busy, and no
  visibility at all; both are displayed here as the default the select shows,
  and a form saved without touching this row leaves the stored value exactly as
  it was. That is what keeps re-saving an old event a no-op on this row.
-->
<template>
  <div class="d-flex flex-column">
    <div class="d-flex flex-row flex-wrap align-center">
      <select
        ref="availability"
        v-model="availability"
        :aria-label="$t('agenda.label.showAs')"
        :title="$t('agenda.label.showAs')"
        class="subtitle-1 my-auto me-4 ignore-vuetify-classes event-availability-select">
        <option
          v-for="option in availabilityOptions"
          :key="option.value"
          :value="option.value">
          {{ option.text }}
        </option>
      </select>
      <select
        ref="visibility"
        v-model="visibility"
        :aria-label="$t('agenda.label.eventVisibility')"
        :title="$t('agenda.label.eventVisibility')"
        class="subtitle-1 my-auto me-2 ignore-vuetify-classes event-visibility-select">
        <option
          v-for="option in visibilityOptions"
          :key="option.value"
          :value="option.value">
          {{ option.text }}
        </option>
      </select>
      <!--
      THE HELP. A tooltip on the desktop row, where the UX asks for the (?)
      Google puts there — and body text on mobile, never a tooltip, for the
      reason AgendaEventFormBusyCoverage already wrote down in this same form:
      a tooltip does not exist on touch, so on the mobile form the sentence
      would be unreachable.

      KNOWN BOUND, written down rather than discovered later: helpAsText is set
      by the mobile form, and which form renders is decided by $root.isMobile —
      which is defined once per app root, in agenda/main.js and again in
      agenda-timeline/main.js (the event dialog is mounted under both), and is
      a VIEWPORT WIDTH test in both, with no touch or hover input. So a touch
      device wider than sm — a tablet in landscape, a touch laptop — gets the
      full form, and there the help is behind a hover again. The property that
      actually matters is "@media (hover: none)".

      BusyCoverage is not a drop-in precedent, but not for the reason one might
      assume: its report is body text AND gated — the root carries v-if=
      "visible" and each line its own guard, so it appears only when somebody
      could not be checked. That is what makes body text cheap there and not
      here: this help sentence has no such gate and would sit under an already
      dense form every time. Closing it on hover rather than on width, or
      accepting the residual, is the PO/designer's call and is recorded with
      EXO-90327.
    -->
      <v-tooltip
        v-if="!helpAsText"
        bottom
        max-width="320">
        <template #activator="{on, attrs}">
          <!--
            aria-label and not title: aria-label is what a screen reader
            announces, and a native title would render the browser's own
            tooltip on top of Vuetify's, showing the same long sentence twice.
          -->
          <v-icon
            v-bind="attrs"
            :aria-label="$t('agenda.availabilityVisibilityHelp')"
            size="16"
            class="icon-default-color my-auto"
            v-on="on">
            fas fa-question-circle
          </v-icon>
        </template>
        <span>{{ $t('agenda.availabilityVisibilityHelp') }}</span>
      </v-tooltip>
    </div>
    <div v-if="helpAsText" class="d-flex flex-row align-start caption mt-1 availability-visibility-help">
      <v-icon size="14" class="me-1 mt-1 icon-default-color">fas fa-question-circle</v-icon>
      <span>{{ $t('agenda.availabilityVisibilityHelp') }}</span>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    /**
     * Whether the help is rendered as body text rather than behind the (?).
     * Set by the mobile form, where a hover does not exist.
     */
    helpAsText: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    availabilityOptions() {
      return [
        {text: this.$t('agenda.availability.busy'), value: 'BUSY'},
        {text: this.$t('agenda.availability.free'), value: 'FREE'},
      ];
    },
    visibilityOptions() {
      return [
        {text: this.$t('agenda.visibility.default'), value: 'DEFAULT'},
        {text: this.$t('agenda.visibility.public'), value: 'PUBLIC'},
        {text: this.$t('agenda.visibility.private'), value: 'PRIVATE'},
      ];
    },
    /**
     * The availability the select shows, and writes back on a pick.
     *
     * Only FREE reads as Free. DEFAULT — what the service writes when nothing
     * was chosen, and so what every event predating this control carries — and
     * a missing value both read as Busy, because that is what they render as
     * everywhere the value is honoured (both ICS writers write OPAQUE for
     * anything that is not FREE).
     *
     * @returns {String} 'BUSY' or 'FREE'
     */
    availability: {
      get() {
        return this.event && this.event.availability === 'FREE' ? 'FREE' : 'BUSY';
      },
      set(value) {
        this.$set(this.event, 'availability', value);
      },
    },
    /**
     * The visibility the select shows, and writes back on a pick.
     *
     * @returns {String} 'DEFAULT', 'PUBLIC' or 'PRIVATE'
     */
    visibility: {
      get() {
        return this.event && this.event.visibility || 'DEFAULT';
      },
      set(value) {
        this.$set(this.event, 'visibility', value);
      },
    },
  },
};
</script>
