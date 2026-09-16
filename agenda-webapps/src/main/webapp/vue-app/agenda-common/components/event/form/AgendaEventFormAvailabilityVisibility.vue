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
    <v-tooltip bottom max-width="320">
      <template #activator="{on, attrs}">
        <v-icon
          v-bind="attrs"
          size="16"
          class="icon-default-color my-auto"
          v-on="on">
          fas fa-question-circle
        </v-icon>
      </template>
      <span>{{ $t('agenda.availabilityVisibilityHelp') }}</span>
    </v-tooltip>
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
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
