import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Landing from './components/Landing';
import HospitalCategory from './components/HospitalCategory';
import Entry from './components/Entry';
import ReceptionPortal from './components/ReceptionPortal';
import DoctorPortal from './components/DoctorPortal';
import HospitalConfig from './components/HospitalConfig';
import PharmacyPortal from './components/PharmacyPortal';
import ProtectedRoute from './components/ProtectedRoute';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <div className="app">
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path="/category/hospital" element={<HospitalCategory />} />
          <Route path="/entry/:facilityType" element={<Entry />} />

          <Route path="/reception" element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <ReceptionPortal />
            </ProtectedRoute>
          } />

          <Route path="/doctor" element={
            <ProtectedRoute allowedRoles={['DOCTOR']}>
              <DoctorPortal />
            </ProtectedRoute>
          } />

          <Route path="/pharmacy" element={
            <ProtectedRoute allowedRoles={['PHARMACY']}>
              <PharmacyPortal />
            </ProtectedRoute>
          } />

          <Route path="/config" element={
            <ProtectedRoute allowedRoles={['ADMIN', 'SUPER_ADMIN']}>
              <HospitalConfig />
            </ProtectedRoute>
          } />
        </Routes>
      </div>
    </BrowserRouter>
  );
}

export default App;