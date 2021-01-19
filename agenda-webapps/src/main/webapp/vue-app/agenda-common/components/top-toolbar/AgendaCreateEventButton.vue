<template>
  <v-btn
    class="btn btn-primary"
    @click="openNewEventForm">
    <v-icon dark>
      mdi-plus
    </v-icon>
    <span class="ml-2 d-none d-lg-inline">
      {{ $t('agenda.button.addEvent') }}
    </span>
  </v-btn>
</template>
<script>
export default {
  methods:{
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
  }
};
</script>
