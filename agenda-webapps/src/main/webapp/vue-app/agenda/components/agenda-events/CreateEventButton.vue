<template>
  <v-row justify="center">
    <create-event-dialog :show-dialog-event="showDialog" />
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
      showDialog: false,
    }
  },
  mounted() {
    this.$root.$on('dialog-event-closed', (data) => {
      this.showDialog = data;
    });
  },
  methods:{
    openEventDialog(){
      this.showDialog = true;
    },
  }
}
</script>
