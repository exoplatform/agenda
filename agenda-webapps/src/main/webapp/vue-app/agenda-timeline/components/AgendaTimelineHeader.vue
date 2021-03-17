<template>
  <v-flex class="agenda-timeline-header d-flex mx-3 my-2">
    <div class="d-flex align-center">
      <a :href="agendaBaseLink" class="body-1 text-uppercase text-sub-title">
        {{ $t('agenda') }}
      </a>
      <agenda-pending-invitation-badge
        :current-space="currentSpace"
        :offset-y="18"
        :offset-x="12" />
    </div>
    <v-spacer />
    <div
      v-if="displayButton"
      :title="addEventButtonTooltip"
      class="d-inline-block">
      <v-btn
        :disabled="!canCreateEvent"
        :title="$t('agenda.button.addEvent')"
        icon
        text
        @click="openEventForm">
        <v-icon>mdi-plus</v-icon>
      </v-btn>
    </div>
  </v-flex>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
    currentCalendar: {
      type: Object,
      default: null
    },
    agendaBaseLink: {
      type: String,
      default: null
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
    canCreateEvent() {
      return !this.currentCalendar || !this.currentCalendar.acl || this.currentCalendar.acl.canCreate;
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
    openEventForm() {
      this.$root.$emit('agenda-event-quick-form', {
        summary: '',
        startDate: new Date(),
        endDate: new Date(),
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