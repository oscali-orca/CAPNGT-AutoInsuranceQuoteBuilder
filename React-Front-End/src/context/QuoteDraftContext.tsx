import {
    createContext,
    useContext,
    useState,
    type ReactNode,
} from "react";

import type {RiskDetails} from "../types/quote";

interface QuoteDraftContextType {
    riskDetails: RiskDetails | null; //null means user didnt submit form yet
    setRiskDetails: (details: RiskDetails | null) => void; //either save validated details or clear draft
}

/*createContext makes shared place where componenets access data (dont need to pass props down tree) */
const QuoteDraftContext = createContext<QuoteDraftContextType | undefined>(undefined);


export function QuoteDraftProvider({ /*makes risk details available to components wrapped inside it */
    children,
}: {
    children: ReactNode;
}) {
    const [riskDetails, setRiskDetails] = useState<RiskDetails | null>(null); /*useState stores risk details*/

    return (
        <QuoteDraftContext.Provider value={{ riskDetails, setRiskDetails }}>
            {children}
        </QuoteDraftContext.Provider>
    );
}

export function useQuoteDraft() { /*custom hook to make accessing context easier*/
    const context = useContext(QuoteDraftContext);
    if (!context) {
        throw new Error("useQuoteDraft must be used within a QuoteDraftProvider");
    }
    return context;
}