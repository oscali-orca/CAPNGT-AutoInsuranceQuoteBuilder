import './App.css'
import {BrowserRouter, Navigate, Route, Routes} from "react-router-dom";

import RiskDetailsForm from './components/RiskDetailsForm'
import CoverageBuilder from './components/CoverageBuilder'
import CoverageComparison from './components/CoverageComparison'


function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/risk-details" replace />} />
        <Route path="/risk-details" element={<RiskDetailsForm />} /> 
        <Route path="/coverage" element={<CoverageBuilder />} />
        <Route path="/comparison" element={<CoverageComparison />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App
