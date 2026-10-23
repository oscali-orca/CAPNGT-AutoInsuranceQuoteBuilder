import { useState } from 'react'
import heroImg from './assets/hero.png'
import reactLogo from './assets/react.svg'
import viteLogo from './assets/vite.svg'
import './App.css'
import {BrowserRouter, Navigate, Route, Routes} from "react-router-dom";

import RiskDetailsForm from './components/RiskDetailsForm'
import CoverageBuilder from './components/CoverageBuilder'


function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/risk-details" replace />} />
        <Route path="/risk-details" element={<RiskDetailsForm />} /> 
        <Route path="/coverage" element={<CoverageBuilder />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App
