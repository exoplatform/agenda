<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <div class="text-no-wrap">
    <div
      v-if="displayButton"
      :title="addEventButtonTooltip"
      class="d-inline-block">
      <v-btn
        :disabled="!canCreateEvent"
        class="btn btn-primary"
        @click="openNewEventForm">
        <v-icon dark>
          mdi-plus
        </v-icon>
        <span class="ms-2 d-none d-lg-inline">
          {{ $t('agenda.button.addEvent') }}
        </span>
      </v-btn>
    </div>
    <agenda-pending-invitation-badge
      :current-space="currentSpace"
      :offset-x="offsetX"
      :offset-y="offsetY" />
  </div>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
    canCreateEvent: {
      type: Boolean,
      default: false,
    },
    offsetX: {
      type: Object,
      default: () => 18,
    },
    offsetY: {
      type: Number,
      default: () => 22,
    },
  },
  data: () => ({
    initialized: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    displayButton() {
      return (!this.isMobile || this.canCreateEvent) && (this.initialized || !eXo.env.portal.spaceId);
    },
    addEventButtonTooltip() {
      if (!this.canCreateEvent) {
        return this.$t('agenda.onlySpaceRedactorCanCreateEvent');
      }
      return '';
    },
  },
  created() {
    this.$root.$on('agenda-application-loaded', () => this.initialized = true);
  },
  methods: {
    openNewEventForm(){
      this.$root.$emit('agenda-event-form', {
        summary: '',
        allDay: false,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      });
    },
  },
};
</script>
