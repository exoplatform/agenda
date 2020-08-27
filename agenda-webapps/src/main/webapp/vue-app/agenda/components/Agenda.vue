<template>
  <v-app class="agenda-application transparent" flat>
    <main>
      <agenda-header />
      <agenda-view :events="events" :calendar="calendar.name" />
    </main>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    calendar: {
      name:'agenda'
    },
    type: 'week',
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
