import '../models/observation.dart';

abstract class ObservationService {
  Future<List<ObservationDraft>> getObservations();
  Future<ObservationDraft> createObservation(ObservationDraft observation);
  Future<ObservationDraft> updateObservation(ObservationDraft observation);
  Future<void> approveObservation(String id);
  Future<List<ObservationDraft>> getQueue();
  Future<ObservationDraft?> getById(String id);
}
