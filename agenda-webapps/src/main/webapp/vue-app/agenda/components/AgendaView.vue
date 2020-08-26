<template>
  <v-row class="fill-height">
    <v-col>
      <v-sheet height="64">
        <agenda-toolbar :title="calendar" />
      </v-sheet>
      <v-sheet height="600">
        <agenda-schedule :event-list="events" />
      </v-sheet>
    </v-col>
  </v-row>
</template>

<script>
export default {
  data: () => ({
    calendar: {name:'agenda'},
    focus: '',
    type: 'week',
    typeToLabel: {
        month: 'Month',
        week: 'Week',
        day: 'Day',
        '4day': '4 Days',
    },
    selectedEvent: {},
    events: []
  }),
  created() {
    this.initEvents();
  },
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
  },
  methods: {
    initEvents() {
      const eventsList = this.$eventService.getEventList();
      eventsList.then( resp => {
        resp.forEach(event =>{
            this.events.push({
                name: event.creator,
                start: event.start,
                end: event.end,
                color: event.color
                })
            })
        }).catch(error =>{
            console.error('Error getting value',error);
        });
    }
  },
};
</script>
