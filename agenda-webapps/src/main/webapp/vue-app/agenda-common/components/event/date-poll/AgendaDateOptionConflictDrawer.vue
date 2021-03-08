<template>
  <exo-drawer
    ref="conflictEventsDrawer"
    right>
    <template slot="title">
      {{ $t('agenda.schedulingConflict') }}
    </template>
    <template slot="content">
      <div v-if="dateOption" class="text-center text-subtitle-2 font-weight-bold pt-2">
        <div class="d-inline-flex">
          <date-format
            :value="dateOption.start"
            :format="dateFormat"
            class="mr-1" />
          <template v-if="!sameDayDates">
            -
            <date-format
              :value="dateOption.end"
              :format="dateFormat"
              class="ml-1" />
          </template>
        </div>
        <div class="d-inline-flex">
          <template v-if="dateOption.allDay">
            {{ $t('agenda.allDay') }}
          </template>
          <template v-else>
            <date-format
              :value="dateOption.start"
              :format="timeFormat"
              class="mr-1" />
            -
            <date-format
              :value="dateOption.end"
              :format="timeFormat"
              class="ml-1 mr-2" />
          </template>
        </div>
      </div>
      <v-list>
        <agenda-date-option-conflict-item
          v-for="event in events"
          :key="event.id"
          :event="event"
          class="mx-2 px-2 py-0 mb-2" />
      </v-list>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data:() => ({
    events: [],
    dateOption: null,
    dateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
    timeFormat: {
      hour: '2-digit',
      minute: '2-digit',
    },
  }),
  computed: {
    sameDayDates() {
      return this.dateOption.start && this.dateOption.end && this.$agendaUtils.areDatesOnSameDay(this.dateOption.start, this.dateOption.end);
    },
  },
  created() {
    this.$root.$on('agenda-conflict-events-drawer-open', (dateOption, events) => {
      this.dateOption = dateOption;
      this.events = events;
      this.open();
    });
  },
  methods: {
    open() {
      if (this.$refs.conflictEventsDrawer) {
        this.$refs.conflictEventsDrawer.open();
      }
    },
  }
};
</script>