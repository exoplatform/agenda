<template>
  <v-row justify="center">
    <create-event :show-dialog-event="showEventDialog" @dialog-event-opened="showEventDialog = $event" />
    <v-btn
      class="btn btn-primary mr-2"
      @click="openEventDialog">
      <v-icon dark left>mdi-plus</v-icon>
      {{ $t('agenda.button.addEvent') }}
    </v-btn>
  </v-row>
</template>
<script>
export default {
  data () {
    return {
      showEventDialog: false,
    }
  },
  methods:{
    openEventDialog(){
      this.showEventDialog = true;
    },
  }
}
</script>
