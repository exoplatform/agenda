<template>
  <exo-drawer
    ref="calendarFilters"
    right
    @opened="drawer = true"
    @closed="drawer = false">
    <template slot="title">
      {{ $t('agenda.filterAgendaTitle') }}
    </template>
    <template slot="content">
      <div class="radio-group-container ps-4">
        <v-radio-group
          v-model="eventType"
          @change="$root.$emit('agenda-event-type-changed', eventType)">
          <v-radio
            :label="$t('agenda.myEvent')"
            value="myEvent" />
          <v-radio
            :label="$t('agenda.declinedEvent')"
            value="declinedEvent" />
          <v-radio
            :label="$t('agenda.allEvent')"
            value="allEvent" />
        </v-radio-group>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {

  data: () => ({
    drawer: false,
    eventType: '',
  }),
  created() {
    this.$root.$on('agenda-calendar-owners-drawer-open', this.open);
  },
  computed: {
    canCreateEvent() {
      return !this.currentCalendar || !this.currentCalendar.acl || this.currentCalendar.acl.canCreate;
    },
  },
  methods: {
    close() {
      this.$refs.calendarFilters.close();
    },
    open(currentSpace) {
      if (!this.eventType){
        this.eventType = currentSpace ? 'allEvent' : 'myEvent';
      }
      this.$refs.calendarFilters.open();
    },
  },
};
</script>