<template>
  <v-btn
    class="btn btn-primary"
    @click="openNewEventForm">
    <v-icon dark left>
      mdi-plus
    </v-icon>
    {{ $t('agenda.button.addEvent') }}
  </v-btn>
</template>
<script>
export default {
  methods:{
    openNewEventForm(){
      this.$root.$emit('agenda-event-form', {});
    },
  }
};
</script>
